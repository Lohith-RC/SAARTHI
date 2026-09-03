/* ==========================================================================
   SAARTHI SPATIAL OS — CLIENT APPLICATION CONTROLLER
   High-Precision 3D Digital Twin, WebSocket Telemetry & Autonomous AI Copilot
   ========================================================================== */

'use strict';

// --------------------------------------------------------------------------
// Application State & Configuration
// --------------------------------------------------------------------------
let scene, camera, renderer, controls;
let racksGroup, particlesMesh;
let currentViewMode = 'normal'; // 'normal' | 'thermal'
let activeCropType = 'mushroom'; // 'mushroom' | 'hydro'
let selectedChamber = 'SAARTHI_001';
let telemetrySocket = null;
let isRecordingMic = false;
let recognition = null;
let currentAudioElement = null;

// Telemetry Real-time State
const telemetry = {
  co2: 845,
  rh: 92.0,
  temp: 22.4,
  fanRpm: 1420,
  fanDuty: 45
};

// Cross-Chamber Multi-Node Fleet State
const fleetChambers = {
  'SAARTHI_001': {
    deviceId: 'SAARTHI_001',
    co2Ppm: 845,
    humidityRh: 92.0,
    tempC: 22.4,
    fanRpm: 1420,
    fanDuty: 45,
    cropType: 'mushroom',
    status: 'OPTIMAL'
  }
};

// Rolling 20-Point History Buffers for Micro-Sparklines
const history = {
  co2: [830, 835, 840, 838, 842, 845, 840, 848, 846, 845, 843, 847, 850, 848, 845, 844, 846, 845, 845, 845],
  rh: [90, 91, 91, 92, 92, 93, 92, 92, 91, 92, 92, 93, 92, 92, 91, 92, 92, 92, 92, 92],
  temp: [22.1, 22.2, 22.2, 22.3, 22.3, 22.4, 22.4, 22.5, 22.4, 22.4, 22.3, 22.4, 22.4, 22.5, 22.4, 22.4, 22.4, 22.4, 22.4, 22.4],
  fan: [1400, 1420, 1420, 1420, 1440, 1420, 1420, 1400, 1420, 1420, 1420, 1440, 1420, 1420, 1420, 1420, 1420, 1420, 1420, 1420]
};

// Client Engine Settings (Tokens stored in session memory or default dev token)
const aiBrainConfig = {
  model: localStorage.getItem('saarthi_ai_model') || 'openai/gpt-oss-120b',
  voiceEngine: localStorage.getItem('saarthi_voice_engine') || 'elevenlabs',
  language: 'en-US',
  operatorToken: sessionStorage.getItem('saarthi_operator_token') || 
                 localStorage.getItem('saarthi_operator_token') || 
                 'KzgSIfXzuJQV53nx881zi8JDswrhD0azpAbcf0dNz072oJLX'
};

// --------------------------------------------------------------------------
// Lifecycle Initialization
// --------------------------------------------------------------------------
document.addEventListener('DOMContentLoaded', () => {
  init3DScene();
  initSpringWebSocket();
  initSpeechRecognition();
  initKeyboardShortcuts();
  syncHUD();
  updateAllSparklines();
  renderFleetSegmentBar();
  loadStoredSettings();
  animate3D();
});

// --------------------------------------------------------------------------
// 1. Three.js 3D Digital Twin Chamber
// --------------------------------------------------------------------------
function init3DScene() {
  const container = document.getElementById('canvas3d-container');
  const canvas = document.getElementById('webgl-canvas');

  scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x06090E, 0.04);

  camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 1000);
  camera.position.set(0, 4.5, 11);

  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.shadowMap.enabled = true;

  // OrbitControls
  controls = new THREE.OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.05;
  controls.maxPolarAngle = Math.PI / 2 + 0.04;
  controls.minDistance = 3;
  controls.maxDistance = 22;

  // Cinematic Lighting Hierarchy
  const ambientLight = new THREE.AmbientLight(0x0a121e, 2.2);
  scene.add(ambientLight);

  const keyLight = new THREE.DirectionalLight(0x10B981, 1.6);
  keyLight.position.set(6, 12, 7);
  scene.add(keyLight);

  const fillLight = new THREE.PointLight(0x38BDF8, 1.8, 20);
  fillLight.position.set(-6, 5, -4);
  scene.add(fillLight);

  // Subtle Floor Grid
  const gridHelper = new THREE.GridHelper(26, 26, 0x10B981, 0x131E2D);
  gridHelper.position.y = -1.4;
  scene.add(gridHelper);

  build3DRacks();
  buildAtmosphericParticles();

  window.addEventListener('resize', onWindowResize);
}

function build3DRacks() {
  if (racksGroup) scene.remove(racksGroup);
  racksGroup = new THREE.Group();

  const metalMat = new THREE.MeshStandardMaterial({
    color: 0x162335,
    metalness: 0.8,
    roughness: 0.25
  });

  const shelfMat = new THREE.MeshStandardMaterial({
    color: 0x0E1724,
    metalness: 0.5,
    roughness: 0.4
  });

  const rackPositions = [-3.6, 0, 3.6];
  rackPositions.forEach((posX) => {
    const rack = new THREE.Group();
    rack.position.set(posX, 0, 0);

    // 4 Vertical Frame Pillars
    const pillarGeo = new THREE.CylinderGeometry(0.04, 0.04, 4.2, 8);
    const pillarOffsets = [
      [-1.1, 0.7, -0.6], [1.1, 0.7, -0.6],
      [-1.1, 0.7, 0.6], [1.1, 0.7, 0.6]
    ];
    pillarOffsets.forEach(pos => {
      const p = new THREE.Mesh(pillarGeo, metalMat);
      p.position.set(...pos);
      rack.add(p);
    });

    // 3 Horizontal Grow Shelves
    const shelfGeo = new THREE.BoxGeometry(2.3, 0.06, 1.3);
    const shelfYLevels = [-0.6, 0.7, 2.0];

    shelfYLevels.forEach(y => {
      const shelf = new THREE.Mesh(shelfGeo, shelfMat);
      shelf.position.y = y;
      rack.add(shelf);

      // Populate Canopy (Mushrooms or Hydroponics)
      if (activeCropType === 'mushroom') {
        populateMushroomsOnTray(rack, y + 0.03);
      } else {
        populateHydroponicsOnTray(rack, y + 0.03);
      }
    });

    racksGroup.add(rack);
  });

  scene.add(racksGroup);
}

function populateMushroomsOnTray(parent, baseY) {
  const capMat = new THREE.MeshStandardMaterial({
    color: currentViewMode === 'thermal' ? 0xF59E0B : 0x10B981,
    roughness: 0.35,
    metalness: 0.1,
    emissive: currentViewMode === 'thermal' ? 0xB45309 : 0x064E3B,
    emissiveIntensity: 0.25
  });

  const stemMat = new THREE.MeshStandardMaterial({ color: 0xF1F5F9, roughness: 0.8 });

  for (let i = 0; i < 6; i++) {
    const cluster = new THREE.Group();
    const cx = (Math.random() - 0.5) * 1.8;
    const cz = (Math.random() - 0.5) * 0.9;
    cluster.position.set(cx, baseY, cz);

    const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.025, 0.035, 0.18, 6), stemMat);
    stem.position.y = 0.09;
    cluster.add(stem);

    const cap = new THREE.Mesh(new THREE.ConeGeometry(0.12, 0.06, 10), capMat);
    cap.position.y = 0.18;
    cap.rotation.x = (Math.random() - 0.5) * 0.2;
    cluster.add(cap);

    parent.add(cluster);
  }
}

function populateHydroponicsOnTray(parent, baseY) {
  const leafMat = new THREE.MeshStandardMaterial({
    color: currentViewMode === 'thermal' ? 0xEF4444 : 0x22C55E,
    roughness: 0.4,
    emissive: currentViewMode === 'thermal' ? 0x991B1B : 0x14532D,
    emissiveIntensity: 0.3
  });

  for (let i = 0; i < 7; i++) {
    const plant = new THREE.Mesh(new THREE.DodecahedronGeometry(0.11, 1), leafMat);
    plant.position.set((Math.random() - 0.5) * 1.9, baseY + 0.1, (Math.random() - 0.5) * 0.9);
    parent.add(plant);
  }
}

function buildAtmosphericParticles() {
  const particleCount = 140;
  const geometry = new THREE.BufferGeometry();
  const positions = new Float32Array(particleCount * 3);

  for (let i = 0; i < particleCount * 3; i += 3) {
    positions[i] = (Math.random() - 0.5) * 18;
    positions[i + 1] = Math.random() * 6 - 1;
    positions[i + 2] = (Math.random() - 0.5) * 14;
  }

  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));

  const material = new THREE.PointsMaterial({
    size: 0.06,
    color: 0x38BDF8,
    transparent: true,
    opacity: 0.45,
    blending: THREE.AdditiveBlending
  });

  particlesMesh = new THREE.Points(geometry, material);
  scene.add(particlesMesh);
}

// Optimization: Pause Three.js render loop when browser tab is inactive to save GPU/battery
let isTabActive = true;
document.addEventListener('visibilitychange', () => {
  const wasHidden = !isTabActive;
  isTabActive = !document.hidden;
  if (wasHidden && isTabActive) {
    requestAnimationFrame(animate3D);
  }
});

function animate3D() {
  if (!isTabActive) return;
  requestAnimationFrame(animate3D);

  if (controls) controls.update();

  // Subtle spore particle float
  if (particlesMesh) {
    const pos = particlesMesh.geometry.attributes.position.array;
    for (let i = 1; i < pos.length; i += 3) {
      pos[i] += 0.003;
      if (pos[i] > 5) pos[i] = -1;
    }
    particlesMesh.geometry.attributes.position.needsUpdate = true;
  }

  if (renderer && scene && camera) {
    renderer.render(scene, camera);
  }
}

function onWindowResize() {
  if (!camera || !renderer) return;
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
}

// --------------------------------------------------------------------------
// 2. Camera Perspectives & View Modes
// --------------------------------------------------------------------------
function setCameraView(preset) {
  document.querySelectorAll('.btn-cam-chip').forEach(el => el.classList.remove('active'));

  if (preset === 'orbit') {
    tweenCamera(0, 4.5, 11, 0, 0.5, 0);
    const btn = document.getElementById('btnCamOrbit');
    if (btn) btn.classList.add('active');
  } else if (preset === 'top') {
    tweenCamera(0, 14, 0.5, 0, 0, 0);
    const btn = document.getElementById('btnCamTop');
    if (btn) btn.classList.add('active');
  } else if (preset === 'canopy') {
    tweenCamera(-1.8, 1.4, 2.2, -1.8, 0.8, 0);
    const btn = document.getElementById('btnCamCanopy');
    if (btn) btn.classList.add('active');
  }
}

function resetCameraHome() {
  setCameraView('orbit');
  showToast('Perspective Centered', 'Reset 3D chamber to 360° overview.');
}

function tweenCamera(x, y, z, tx, ty, tz) {
  if (!camera || !controls) return;
  camera.position.set(x, y, z);
  controls.target.set(tx, ty, tz);
}

function toggleThermalMode() {
  currentViewMode = currentViewMode === 'normal' ? 'thermal' : 'normal';
  const btn = document.getElementById('btnToggleThermal');
  if (btn) btn.classList.toggle('active', currentViewMode === 'thermal');

  build3DRacks();
  showToast(
    currentViewMode === 'thermal' ? 'IR Thermal View' : 'Standard Bioluminescence',
    currentViewMode === 'thermal' ? 'Virtual infrared heat gradient active.' : 'Restored natural light spectrum.'
  );
}

// --------------------------------------------------------------------------
// 3. Telemetry Ingestion & WebSocket Sync
// --------------------------------------------------------------------------
function initSpringWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsUrl = `${protocol}//${window.location.host}/ws/telemetry`;

  try {
    telemetrySocket = new WebSocket(wsUrl);

    telemetrySocket.onopen = () => {
      console.log('SAARTHI WebSocket connected:', wsUrl);
      const statusText = document.getElementById('systemStatusText');
      const beacon = document.querySelector('.status-beacon');
      if (statusText) statusText.textContent = 'AUTONOMOUS LOOP: LIVE';
      if (beacon) {
        beacon.style.background = 'var(--emerald)';
        beacon.style.boxShadow = '0 0 8px var(--emerald)';
      }
    };

    telemetrySocket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (payload.type === 'FLEET_SNAPSHOT' && Array.isArray(payload.chambers)) {
          payload.chambers.forEach(c => { if (c.deviceId) fleetChambers[c.deviceId] = c; });
          renderFleetSegmentBar();
        } else if (payload.type === 'CHAMBER_UPDATE' && payload.record) {
          fleetChambers[payload.record.deviceId] = payload.record;
          if (payload.record.deviceId === selectedChamber) {
            applyRecordToHUD(payload.record);
          }
          renderFleetSegmentBar();
        } else if (payload.deviceId) {
          fleetChambers[payload.deviceId] = payload;
          if (payload.deviceId === selectedChamber) {
            applyRecordToHUD(payload);
          }
        }
      } catch (err) {
        console.warn('Malformed telemetry frame:', err);
      }
    };

    telemetrySocket.onclose = () => {
      const statusText = document.getElementById('systemStatusText');
      const beacon = document.querySelector('.status-beacon');
      if (statusText) statusText.textContent = 'RECONNECTING STREAM...';
      if (beacon) {
        beacon.style.background = 'var(--amber)';
        beacon.style.boxShadow = '0 0 8px var(--amber)';
      }
      setTimeout(initSpringWebSocket, 3500);
    };
  } catch (err) {
    console.warn('WebSocket unavailable, running on local poll loop.');
  }
}

function applyRecordToHUD(record) {
  if (!record) return;
  if (record.co2Ppm != null) telemetry.co2 = Number(record.co2Ppm);
  if (record.humidityRh != null) telemetry.rh = Number(record.humidityRh);
  if (record.tempC != null) telemetry.temp = Number(record.tempC);
  if (record.fanRpm != null) telemetry.fanRpm = Number(record.fanRpm);
  if (record.fanDuty != null) telemetry.fanDuty = Number(record.fanDuty);

  pushHistory('co2', telemetry.co2);
  pushHistory('rh', telemetry.rh);
  pushHistory('temp', telemetry.temp);
  pushHistory('fan', telemetry.fanRpm);

  syncHUD();
  updateAllSparklines();
}

function pushHistory(metric, val) {
  if (history[metric]) {
    history[metric].push(val);
    if (history[metric].length > 20) history[metric].shift();
  }
}

function syncHUD() {
  // CO2
  const co2El = document.getElementById('valCO2Num');
  const co2Stat = document.getElementById('valCO2Status');
  if (co2El) co2El.innerHTML = `${Math.round(telemetry.co2)} <small style="font-size:0.8rem;color:var(--text-muted);">ppm</small>`;
  if (co2Stat) {
    if (telemetry.co2 > 1300) { co2Stat.className = 'vital-pill danger'; co2Stat.textContent = 'CRITICAL'; }
    else if (telemetry.co2 > 950) { co2Stat.className = 'vital-pill warn'; co2Stat.textContent = 'WARNING'; }
    else { co2Stat.className = 'vital-pill ok'; co2Stat.textContent = 'OPTIMAL'; }
  }

  // Humidity
  const rhEl = document.getElementById('valRHNum');
  const rhStat = document.getElementById('valRHStatus');
  if (rhEl) rhEl.innerHTML = `${telemetry.rh.toFixed(1)} <small style="font-size:0.8rem;color:var(--text-muted);">%</small>`;
  if (rhStat) {
    if (telemetry.rh < 75) { rhStat.className = 'vital-pill danger'; rhStat.textContent = 'DRY'; }
    else if (telemetry.rh < 85) { rhStat.className = 'vital-pill warn'; rhStat.textContent = 'LOW'; }
    else { rhStat.className = 'vital-pill ok'; rhStat.textContent = 'OPTIMAL'; }
  }

  // Temp
  const tempEl = document.getElementById('valTempNum');
  const tempStat = document.getElementById('valTempStatus');
  if (tempEl) tempEl.innerHTML = `${telemetry.temp.toFixed(1)} <small style="font-size:0.8rem;color:var(--text-muted);">°C</small>`;
  if (tempStat) {
    if (telemetry.temp > 28 || telemetry.temp < 15) { tempStat.className = 'vital-pill danger'; tempStat.textContent = 'DANGER'; }
    else if (telemetry.temp > 25) { tempStat.className = 'vital-pill warn'; tempStat.textContent = 'WARM'; }
    else { tempStat.className = 'vital-pill ok'; tempStat.textContent = 'OPTIMAL'; }
  }

  // Fan
  const fanEl = document.getElementById('valFanNum');
  const fanDutyEl = document.getElementById('valFanDutyText');
  if (fanEl) fanEl.innerHTML = `${Math.round(telemetry.fanRpm).toLocaleString()} <small style="font-size:0.8rem;color:var(--text-muted);">RPM</small>`;
  if (fanDutyEl) fanDutyEl.textContent = `${telemetry.fanDuty}% Duty`;
}

// --------------------------------------------------------------------------
// 4. Micro-Sparklines Generator
// --------------------------------------------------------------------------
function updateAllSparklines() {
  renderSparkline('sparkAreaCO2', 'sparkLineCO2', history.co2, 400, 1800);
  renderSparkline('sparkAreaRH', 'sparkLineRH', history.rh, 40, 100);
  renderSparkline('sparkAreaTemp', 'sparkLineTemp', history.temp, 10, 35);
  renderSparkline('sparkAreaFan', 'sparkLineFan', history.fan, 0, 3000);
}

function renderSparkline(areaId, lineId, dataArray, minRange, maxRange) {
  const areaEl = document.getElementById(areaId);
  const lineEl = document.getElementById(lineId);
  if (!areaEl || !lineEl || !dataArray || dataArray.length < 2) return;

  const w = 160;
  const h = 28;
  const minVal = Math.min(...dataArray, minRange);
  const maxVal = Math.max(...dataArray, maxRange);
  const range = (maxVal - minVal) || 1;

  const stepX = w / (dataArray.length - 1);
  let linePath = '';

  dataArray.forEach((val, i) => {
    const x = i * stepX;
    const y = h - ((val - minVal) / range) * (h - 4) - 2;
    linePath += `${i === 0 ? 'M' : ' L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
  });

  const areaPath = `${linePath} L ${w} ${h} L 0 ${h} Z`;
  lineEl.setAttribute('d', linePath);
  areaEl.setAttribute('d', areaPath);
}

// --------------------------------------------------------------------------
// 5. Multi-Chamber Fleet Switcher
// --------------------------------------------------------------------------
function renderFleetSegmentBar() {
  const container = document.getElementById('fleetSegmentBar');
  if (!container) return;

  const ids = Object.keys(fleetChambers).sort();
  if (ids.length === 0) return;

  container.innerHTML = ids.map(id => {
    const rec = fleetChambers[id] || {};
    const isActive = id === selectedChamber ? ' active' : '';
    const statusDot = rec.status === 'CRITICAL' ? ' danger' : (rec.status === 'WARNING' ? ' warn' : '');
    const cropLabel = rec.cropType === 'hydro' ? 'Hydro Basil' : 'Oyster Mushroom';

    return `
      <button class="fleet-pill${isActive}" onclick="selectChamber('${escapeHtml(id)}')">
        <span class="fleet-pill-dot${statusDot}"></span>
        <span>${escapeHtml(id)} (${cropLabel})</span>
      </button>
    `;
  }).join('');
}

function selectChamber(deviceId) {
  selectedChamber = deviceId;
  renderFleetSegmentBar();

  const rec = fleetChambers[deviceId];
  if (rec) {
    if (rec.cropType && rec.cropType !== activeCropType) {
      activeCropType = rec.cropType;
      build3DRacks();
    }
    applyRecordToHUD(rec);
  }
  showToast('Chamber Switched', `Monitoring ${deviceId} telemetry.`);
}

// --------------------------------------------------------------------------
// 6. Conversational AI Agronomist Copilot
// --------------------------------------------------------------------------
function setCopilotState(state) {
  const badge = document.getElementById('copilotStatusBadge');
  const text = document.getElementById('copilotStateText');
  if (!badge || !text) return;

  badge.classList.remove('active');

  if (state === 'listening') {
    badge.classList.add('active');
    text.textContent = 'AI LISTENING...';
    text.style.color = 'var(--sky)';
  } else if (state === 'thinking') {
    badge.classList.add('active');
    text.textContent = 'AI REASONING...';
    text.style.color = 'var(--amber)';
  } else if (state === 'speaking') {
    badge.classList.add('active');
    text.textContent = 'AI SPEAKING...';
    text.style.color = 'var(--emerald)';
  } else {
    text.textContent = 'AI COPILOT: IDLE';
    text.style.color = 'var(--text-secondary)';
  }
}

async function handleQuerySubmit(event) {
  if (event) event.preventDefault();
  const input = document.getElementById('copilotQueryInput');
  if (!input) return;
  const text = input.value.trim();
  if (!text) return;
  input.value = '';

  processCopilotQuery(text);
}

function executeQuickDirective(type) {
  if (type === 'status') {
    processCopilotQuery("Give me a concise health report on Chamber 1 climate parameters.");
  } else if (type === 'ventilate') {
    processCopilotQuery("Initiate a 10-minute exhaust fan ventilation purge to drop CO2.");
  } else if (type === 'disease') {
    processCopilotQuery("What are the early indicators and remediation steps for Trichoderma mold?");
  }
}

async function processCopilotQuery(query) {
  appendChatMessage('user', query);
  setCopilotState('thinking');

  // Attempt Spring Boot REST API
  try {
    const headers = { 'Content-Type': 'application/json' };
    if (aiBrainConfig.operatorToken) {
      headers['X-Operator-Token'] = aiBrainConfig.operatorToken;
    }

    const res = await fetch('/api/v1/ai/chat', {
      method: 'POST',
      headers,
      body: JSON.stringify({
        query: query,
        model: aiBrainConfig.model,
        language: aiBrainConfig.language
      })
    });

    if (res.ok) {
      const data = await res.json();
      appendChatMessage('assistant', data.replyText || "Chamber state analyzed.");
      if (data.executedAction) executeActuationDirective(data.executedAction);
      speakCopilot(data.replyText);
      return;
    }
  } catch (err) {
    console.warn('Backend chat bypassed, using local agronomy rule engine:', err);
  }

  // Fallback Local Agronomic Engine
  localAgronomyEngine(query);
}

function localAgronomyEngine(query) {
  const q = query.toLowerCase();
  let reply = "";

  if (q.includes('ventilate') || q.includes('fan') || q.includes('purge')) {
    telemetry.fanRpm = 2400;
    telemetry.fanDuty = 85;
    syncHUD();
    reply = "Exhaust purge cycle engaged. FAE blower ramped to 85% duty (2,400 RPM). Target CO2 reduction to 750 ppm.";
  } else if (q.includes('trichoderma') || q.includes('green') || q.includes('mold')) {
    reply = "Trichoderma detected. Immediately isolate the infected block, spot-treat margins with 3% food-grade H2O2, and lower relative humidity below 85% temporarily.";
  } else if (q.includes('status') || q.includes('report') || q.includes('health')) {
    reply = `Chamber 1 is stable. Carbon dioxide is ${Math.round(telemetry.co2)} ppm, relative humidity is ${telemetry.rh.toFixed(1)}%, and temperature is ${telemetry.temp.toFixed(1)}°C.`;
  } else {
    reply = `Analyzed query: "${query}". Chamber 1 regulation is active. Climate parameters are within normal growth bounds.`;
  }

  setTimeout(() => {
    appendChatMessage('assistant', reply);
    speakCopilot(reply);
  }, 400);
}

function executeActuationDirective(action) {
  if (!action || !action.action) return;
  const act = action.action.toUpperCase();
  if (act === 'VENTILATE' || act === 'RELAY_ON') {
    telemetry.fanRpm = action.rpm || 2400;
    telemetry.fanDuty = 85;
    syncHUD();
    showToast('Autonomous Actuation', 'Exhaust blower purged via AI directive.');
  }
}

function appendChatMessage(sender, text) {
  const list = document.getElementById('chatMessageList');
  if (!list) return;

  const bubble = document.createElement('div');
  bubble.className = `chat-bubble ${sender}`;
  bubble.innerHTML = sender === 'user'
    ? `<strong>You:</strong> ${escapeHtml(text)}`
    : `<strong>Saarthi Copilot:</strong> ${escapeHtml(text)}`;

  list.appendChild(bubble);
  list.scrollTop = list.scrollHeight;
}

// --------------------------------------------------------------------------
// 7. Voice Interaction & Speech Synthesis
// --------------------------------------------------------------------------
function initSpeechRecognition() {
  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRec) return;

  recognition = new SpeechRec();
  recognition.continuous = false;
  recognition.interimResults = false;

  recognition.onstart = () => {
    isRecordingMic = true;
    const btn = document.getElementById('btnMicTalk');
    if (btn) btn.classList.add('recording');
    setCopilotState('listening');
  };

  recognition.onresult = (e) => {
    const text = e.results[0][0].transcript;
    stopMic();
    processCopilotQuery(text);
  };

  recognition.onerror = () => {
    stopMic();
    setCopilotState('idle');
  };

  recognition.onend = () => {
    stopMic();
    setCopilotState('idle');
  };
}

function toggleVoiceInput() {
  if (!recognition) {
    showToast('Mic Unsupported', 'Web Speech API supported in Chrome, Edge, and Safari.');
    return;
  }
  if (!isRecordingMic) {
    try { recognition.start(); } catch (e) {}
  } else {
    recognition.stop();
  }
}

function stopMic() {
  isRecordingMic = false;
  const btn = document.getElementById('btnMicTalk');
  if (btn) btn.classList.remove('recording');
}

async function speakCopilot(text) {
  setCopilotState('speaking');

  // Try ElevenLabs via backend
  if (aiBrainConfig.voiceEngine === 'elevenlabs' && aiBrainConfig.operatorToken) {
    try {
      if (currentAudioElement) {
        currentAudioElement.pause();
        currentAudioElement = null;
      }

      const res = await fetch('/api/v1/ai/tts', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Operator-Token': aiBrainConfig.operatorToken
        },
        body: JSON.stringify({ text })
      });

      if (res.ok) {
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const audio = new Audio(url);
        currentAudioElement = audio;

        audio.onended = () => {
          setCopilotState('idle');
          URL.revokeObjectURL(url);
          currentAudioElement = null;
        };

        audio.onerror = () => fallbackWebSpeech(text);
        await audio.play();
        return;
      }
    } catch (e) {}
  }

  fallbackWebSpeech(text);
}

function fallbackWebSpeech(text) {
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.rate = 1.05;
    u.onstart = () => setCopilotState('speaking');
    u.onend = () => setCopilotState('idle');
    u.onerror = () => setCopilotState('idle');
    window.speechSynthesis.speak(u);
  } else {
    setCopilotState('idle');
  }
}

// --------------------------------------------------------------------------
// 8. Modals & Settings Management
// --------------------------------------------------------------------------
function toggleConversationDrawer() {
  const drawer = document.getElementById('conversationDrawer');
  if (drawer) drawer.classList.toggle('open');
}

function openSettingsModal() {
  const modal = document.getElementById('settingsModal');
  if (modal) modal.style.display = 'flex';
}

function closeSettingsModal() {
  const modal = document.getElementById('settingsModal');
  if (modal) modal.style.display = 'none';
}

function loadStoredSettings() {
  const modelSel = document.getElementById('settingModelSelect');
  const voiceSel = document.getElementById('settingVoiceSelect');
  const tokenInp = document.getElementById('settingTokenInput');

  if (modelSel && aiBrainConfig.model) modelSel.value = aiBrainConfig.model;
  if (voiceSel && aiBrainConfig.voiceEngine) voiceSel.value = aiBrainConfig.voiceEngine;
  if (tokenInp && aiBrainConfig.operatorToken) tokenInp.value = aiBrainConfig.operatorToken;
}

function saveSettings() {
  const modelSel = document.getElementById('settingModelSelect');
  const voiceSel = document.getElementById('settingVoiceSelect');
  const tokenInp = document.getElementById('settingTokenInput');

  if (modelSel) {
    aiBrainConfig.model = modelSel.value;
    localStorage.setItem('saarthi_ai_model', modelSel.value);
  }
  if (voiceSel) {
    aiBrainConfig.voiceEngine = voiceSel.value;
    localStorage.setItem('saarthi_voice_engine', voiceSel.value);
  }
  if (tokenInp) {
    aiBrainConfig.operatorToken = tokenInp.value.trim();
    sessionStorage.setItem('saarthi_operator_token', aiBrainConfig.operatorToken);
  }

  closeSettingsModal();
  showToast('Settings Saved', 'AI Model and Operator Token active.');
}

function handleModalBackdropClick(e, modalId) {
  if (e.target && e.target.id === modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.style.display = 'none';
  }
}

// Vision Pathology Modal
function openVisionModal() {
  const modal = document.getElementById('visionModal');
  if (modal) modal.style.display = 'flex';
}

function closeVisionModal() {
  const modal = document.getElementById('visionModal');
  if (modal) modal.style.display = 'none';
}

function simulatePathogen(type) {
  const title = document.getElementById('visionDiagnosisTitle');
  const desc = document.getElementById('visionDiagnosisDesc');
  const directive = document.getElementById('visionDirectiveText');

  if (type === 'trichoderma') {
    title.textContent = "Trichoderma harzianum (Green Mold Infection)";
    desc.textContent = "Dense white mycelium turning characteristic dark green. Aggressive sporulation threatens surrounding fruiting blocks.";
    directive.textContent = "Isolate block immediately. Apply 3% food-grade hydrogen peroxide spot-treatment and increase FAE exhaust duty to 80%.";
  } else if (type === 'suffocation') {
    title.textContent = "High CO2 Stipe Elongation (Suffocation)";
    desc.textContent = "Elongated stems with stunted, tiny caps. Direct physiological indicator that carbon dioxide exceeds 1,200 ppm.";
    directive.textContent = "Engage 15-minute emergency ventilation cycle. Target ambient CO2 below 850 ppm.";
  } else {
    title.textContent = "Pleurotus ostreatus (Healthy Fruiting)";
    desc.textContent = "Convex cap morphology with optimal margin expansion. Primordia clusters indicate balanced CO2 and humidity baselines.";
    directive.textContent = "Maintain current FAE blower duty at 45% (1,420 RPM). Schedule harvest in 18 to 24 hours.";
  }
}

function executeVisionDirective() {
  const directive = document.getElementById('visionDirectiveText');
  closeVisionModal();
  showToast('Directive Executed', directive ? directive.textContent : 'Directive applied.');
}

// Virtual Simulation Overrides
function simulateBreathSpike() {
  telemetry.co2 = 1480;
  pushHistory('co2', telemetry.co2);
  syncHUD();
  updateAllSparklines();
  showToast('Simulation Spike', 'Simulated CO2 breath test at 1,480 ppm [WARNING].');
}

function onTestbenchSlider(metric, val) {
  if (metric === 'co2') {
    telemetry.co2 = Number(val);
    const lbl = document.getElementById('testbenchCO2Val');
    if (lbl) lbl.textContent = `${val} ppm`;
    pushHistory('co2', telemetry.co2);
    syncHUD();
    updateAllSparklines();
  }
}

// --------------------------------------------------------------------------
// 9. Keyboard Navigation & Toast Feedback
// --------------------------------------------------------------------------
function initKeyboardShortcuts() {
  window.addEventListener('keydown', (e) => {
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT' || e.target.tagName === 'TEXTAREA') return;

    if (e.key === '1') setCameraView('orbit');
    else if (e.key === '2') setCameraView('top');
    else if (e.key === '3') setCameraView('canopy');
    else if (e.key === 't' || e.key === 'T') toggleThermalMode();
    else if (e.key === 's' || e.key === 'S') simulateBreathSpike();
    else if (e.key === 'm' || e.key === 'M') toggleVoiceInput();
    else if (e.key === 'Escape') {
      closeSettingsModal();
      closeVisionModal();
      const drawer = document.getElementById('conversationDrawer');
      if (drawer) drawer.classList.remove('open');
    }
  });
}

function showToast(title, message, duration = 3400) {
  const cluster = document.getElementById('toastCluster');
  if (!cluster) return;

  const toast = document.createElement('div');
  toast.className = 'toast-item';
  toast.innerHTML = `
    <span>✦</span>
    <div>
      <strong style="color:var(--text-primary);display:block;font-size:0.78rem;">${escapeHtml(title)}</strong>
      <span style="color:var(--text-secondary);font-size:0.72rem;">${escapeHtml(message)}</span>
    </div>
  `;

  cluster.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(20px)';
    setTimeout(() => toast.remove(), 250);
  }, duration);
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
