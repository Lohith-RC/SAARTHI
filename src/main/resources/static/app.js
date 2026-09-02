/* ==========================================================================
   SAARTHI 3D-OS: JAVA 21 + SPRING BOOT 3.x WEBSOCKETS + GEMINI AI CORE
   HOLOGRAPHIC "JARVIS" SPATIAL EDITION (Three.js WebGL 2.0 + FFT Visualizer)
   ========================================================================== */

let scene, camera, renderer, controls;
let racksGroup, particlesMesh;
let cropType = 'mushroom'; // 'mushroom' or 'hydro'
let currentViewMode = 'normal'; // 'normal' or 'thermal'
let isAudioFxEnabled = true;
let isRecordingMic = false;
let recognition;
let telemetrySocket = null;
let isHudFocused = false;

// Telemetry State
const telemetry = {
  co2: 845,
  rh: 92,
  temp: 22.4,
  fanRpm: 1420,
  fanDuty: 45
};

// Rolling History Buffers for Real-Time Sparklines (20 data points)
const history = {
  co2: [830, 835, 840, 838, 842, 845, 840, 848, 846, 845, 843, 847, 850, 848, 845, 844, 846, 845, 845, 845],
  rh: [90, 91, 91, 92, 92, 93, 92, 92, 91, 92, 92, 93, 92, 92, 91, 92, 92, 92, 92, 92],
  temp: [22.1, 22.2, 22.2, 22.3, 22.3, 22.4, 22.4, 22.5, 22.4, 22.4, 22.3, 22.4, 22.4, 22.5, 22.4, 22.4, 22.4, 22.4, 22.4, 22.4],
  fan: [1400, 1420, 1420, 1420, 1440, 1420, 1420, 1400, 1420, 1420, 1420, 1440, 1420, 1420, 1420, 1420, 1420, 1420, 1420, 1420]
};

// AI Brain Configuration & State — API keys live server-side only.
// No provider keys are stored or transmitted from the browser.
const aiBrainConfig = {
  model: localStorage.getItem('saarthi_ai_model') || 'openai/gpt-oss-120b',
  voiceEngine: localStorage.getItem('saarthi_voice_engine') || 'elevenlabs',
  language: 'en-US',
  operatorToken: sessionStorage.getItem('saarthi_operator_token') || ''
};

// Attach X-Operator-Token to operator-gated API calls when one has been entered.
function getOperatorHeaders(extra = {}) {
  const headers = { 'Content-Type': 'application/json', ...extra };
  if (aiBrainConfig.operatorToken) {
    headers['X-Operator-Token'] = aiBrainConfig.operatorToken;
  }
  return headers;
}

// Prompt the operator once (per tab session) for the operator token.
function ensureOperatorToken() {
  if (aiBrainConfig.operatorToken) return true;
  const token = prompt('Enter the SAARTHI operator token to enable chat, voice, and control:', '');
  if (token) {
    aiBrainConfig.operatorToken = token.trim();
    sessionStorage.setItem('saarthi_operator_token', aiBrainConfig.operatorToken);
    return true;
  }
  return false;
}

let currentOrbState = 'idle'; // 'idle' | 'listening' | 'thinking' | 'speaking'
let audioCtx = null;
let visualizerAngle = 0;
let currentAudioElement = null;

// Holographic Avatar Face State
let avatarCanvas, avatarCtx;
let currentEmotion = 'helpful'; // 'helpful' | 'serious' | 'thinking' | 'happy'
let eyeBlinkProgress = 0;
let isBlinking = false;
let blinkTimer = 0;
let eyeGazeX = 0;
let eyeGazeY = 0;
let targetGazeX = 0;
let targetGazeY = 0;
let mouthOpenness = 0;
let speechWavePhase = 0;

document.addEventListener('DOMContentLoaded', () => {
  init3DScene();
  initBrainConfig();
  initSpeechRecognition();
  initSpringWebSocket();
  initAvatarFaceCanvas();
  initKeyboardShortcuts();
  updateAllSparklines();
  animate3D();

  // Auto-launch guided tour for first-time operators
  setTimeout(() => {
    if (!localStorage.getItem('saarthi_tour_completed')) {
      startOnboardingTour(false);
    }
  }, 1200);
});

// 1. INITIALIZE THREE.JS 3D SCENE
function init3DScene() {
  const container = document.getElementById('canvas3d-container');
  const canvas = document.getElementById('webgl-canvas');

  scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x05080C, 0.035);

  camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 1000);
  camera.position.set(0, 5, 12);

  renderer = new THREE.WebGLRenderer({ canvas: canvas, antialias: true, alpha: true });
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.shadowMap.enabled = true;

  // OrbitControls
  controls = new THREE.OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.05;
  controls.maxPolarAngle = Math.PI / 2 + 0.05;
  controls.minDistance = 3;
  controls.maxDistance = 25;

  // Lighting
  const ambientLight = new THREE.AmbientLight(0x0a141e, 2.5);
  scene.add(ambientLight);

  const keyLight = new THREE.DirectionalLight(0x00F5A0, 1.8);
  keyLight.position.set(5, 12, 8);
  scene.add(keyLight);

  const fillLight = new THREE.PointLight(0x00D2FF, 2, 20);
  fillLight.position.set(-6, 4, -4);
  scene.add(fillLight);

  // Floor Grid
  const gridHelper = new THREE.GridHelper(30, 30, 0x00F5A0, 0x112233);
  gridHelper.position.y = -1.5;
  scene.add(gridHelper);

  // Build 3D Grow Racks
  build3DRacks();

  // Floating Bioluminescent Spores / Particles
  buildBioluminescentParticles();

  window.addEventListener('resize', onWindowResize);
}

// 2. PROCEDURAL 3D GROW RACKS GENERATOR
function build3DRacks() {
  if (racksGroup) scene.remove(racksGroup);
  racksGroup = new THREE.Group();

  const metalMat = new THREE.MeshStandardMaterial({
    color: 0x1a2634,
    metalness: 0.85,
    roughness: 0.2
  });

  const shelfMat = new THREE.MeshStandardMaterial({
    color: 0x0d1520,
    metalness: 0.5,
    roughness: 0.4
  });

  // Create 3 Racks
  const rackPositions = [-3.8, 0, 3.8];
  rackPositions.forEach((posX, idx) => {
    const rack = new THREE.Group();
    rack.position.set(posX, 0, 0);

    // 4 Vertical Pillars
    const pillarGeo = new THREE.CylinderGeometry(0.06, 0.06, 5, 8);
    const pCoords = [[-1.2, -0.6], [1.2, -0.6], [-1.2, 0.6], [1.2, 0.6]];
    pCoords.forEach(coord => {
      const pillar = new THREE.Mesh(pillarGeo, metalMat);
      pillar.position.set(coord[0], 1, coord[1]);
      rack.add(pillar);
    });

    // 3 Tiers of Trays
    const trayGeo = new THREE.BoxGeometry(2.4, 0.12, 1.2);
    for (let t = 0; t < 3; t++) {
      const trayY = -0.5 + (t * 1.5);
      const tray = new THREE.Mesh(trayGeo, shelfMat);
      tray.position.set(0, trayY, 0);
      rack.add(tray);

      // Substrate Bed
      const bedGeo = new THREE.BoxGeometry(2.25, 0.08, 1.05);
      const bedMat = new THREE.MeshStandardMaterial({ color: cropType === 'mushroom' ? 0x221a15 : 0x081c15 });
      const bed = new THREE.Mesh(bedGeo, bedMat);
      bed.position.set(0, trayY + 0.08, 0);
      rack.add(bed);

      // Populate Crops (Mushrooms vs Hydroponic Lettuce)
      if (cropType === 'mushroom') {
        populateMushroomsOnTray(rack, trayY + 0.12);
      } else {
        populateHydroponicPlantsOnTray(rack, trayY + 0.12);
      }

      // Overhead LED Strip for Tray
      const ledGeo = new THREE.BoxGeometry(2.2, 0.04, 0.06);
      const ledMat = new THREE.MeshBasicMaterial({ color: cropType === 'mushroom' ? 0x00D2FF : 0xFF007F });
      const led = new THREE.Mesh(ledGeo, ledMat);
      led.position.set(0, trayY + 1.35, 0);
      rack.add(led);
    }

    racksGroup.add(rack);
  });

  scene.add(racksGroup);
}

// 3. PROCEDURAL 3D MUSHROOMS
function populateMushroomsOnTray(parent, baseY) {
  const stemMat = new THREE.MeshStandardMaterial({ color: 0xe6e6e6, roughness: 0.4 });
  const capMat = new THREE.MeshStandardMaterial({
    color: currentViewMode === 'thermal' ? 0xFF3366 : 0x00F5A0,
    emissive: currentViewMode === 'thermal' ? 0xFF0033 : 0x00A86B,
    emissiveIntensity: 0.4,
    roughness: 0.3
  });

  for (let x = -0.9; x <= 0.9; x += 0.28) {
    for (let z = -0.35; z <= 0.35; z += 0.32) {
      const offsetX = x + (Math.random() - 0.5) * 0.1;
      const offsetZ = z + (Math.random() - 0.5) * 0.1;
      const scale = 0.6 + Math.random() * 0.6;

      const cluster = new THREE.Group();
      cluster.position.set(offsetX, baseY, offsetZ);

      const stemGeo = new THREE.CylinderGeometry(0.025 * scale, 0.035 * scale, 0.22 * scale, 6);
      const stem = new THREE.Mesh(stemGeo, stemMat);
      stem.position.y = 0.11 * scale;
      cluster.add(stem);

      const capGeo = new THREE.SphereGeometry(0.1 * scale, 8, 6, 0, Math.PI * 2, 0, Math.PI * 0.5);
      const cap = new THREE.Mesh(capGeo, capMat);
      cap.position.y = 0.21 * scale;
      cluster.add(cap);

      parent.add(cluster);
    }
  }
}

// 4. PROCEDURAL 3D HYDROPONIC PLANTS
function populateHydroponicPlantsOnTray(parent, baseY) {
  const leafMat = new THREE.MeshStandardMaterial({
    color: 0x00F5A0,
    emissive: 0x004422,
    emissiveIntensity: 0.3,
    roughness: 0.5
  });

  for (let x = -0.9; x <= 0.9; x += 0.35) {
    for (let z = -0.35; z <= 0.35; z += 0.35) {
      const plant = new THREE.Group();
      plant.position.set(x, baseY, z);

      for (let l = 0; l < 5; l++) {
        const leafGeo = new THREE.ConeGeometry(0.08, 0.18, 4);
        const leaf = new THREE.Mesh(leafGeo, leafMat);
        leaf.rotation.z = (l * Math.PI / 2.5) - 0.6;
        leaf.rotation.y = l * 1.2;
        leaf.position.y = 0.08;
        plant.add(leaf);
      }
      parent.add(plant);
    }
  }
}

// 5. BIOLUMINESCENT SPORE PARTICLES
function buildBioluminescentParticles() {
  const particleCount = 250;
  const geometry = new THREE.BufferGeometry();
  const positions = new Float32Array(particleCount * 3);

  for (let i = 0; i < particleCount * 3; i += 3) {
    positions[i] = (Math.random() - 0.5) * 16;
    positions[i + 1] = Math.random() * 8 - 1;
    positions[i + 2] = (Math.random() - 0.5) * 12;
  }

  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));

  const material = new THREE.PointsMaterial({
    size: 0.08,
    color: 0x00F5A0,
    transparent: true,
    opacity: 0.75,
    blending: THREE.AdditiveBlending
  });

  particlesMesh = new THREE.Points(geometry, material);
  scene.add(particlesMesh);
}

// 6. ANIMATION LOOP & PAGE VISIBILITY THROTTLER (PERF-03)
let isPageVisible = true;
let lastHiddenRender = 0;

document.addEventListener('visibilitychange', () => {
  isPageVisible = !document.hidden;
});

function animate3D(now) {
  requestAnimationFrame(animate3D);

  // When tab is hidden/backgrounded, throttle to 1 FPS to eliminate GPU thermal load and battery drain
  if (!isPageVisible) {
    if (now - lastHiddenRender < 1000) {
      return;
    }
    lastHiddenRender = now;
  }

  if (particlesMesh) {
    const pos = particlesMesh.geometry.attributes.position.array;
    for (let i = 1; i < pos.length; i += 3) {
      pos[i] += 0.005 * (telemetry.fanRpm / 1000);
      if (pos[i] > 7) pos[i] = -1;
    }
    particlesMesh.geometry.attributes.position.needsUpdate = true;
    particlesMesh.rotation.y += 0.001;
  }

  // Render Procedural Animated Cyber Face Avatar (Eyes & Lip-Sync Mouth)
  if (isPageVisible) {
    drawAvatarFace();
  }

  controls.update();
  renderer.render(scene, camera);
}

function onWindowResize() {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
}

// 7. CAMERA PERSPECTIVE PRESETS
function setCameraView(preset) {
  playTone(880, 'sine', 0.05);
  document.querySelectorAll('.btn-preset').forEach(b => b.classList.remove('active'));

  if (preset === 'orbit') {
    tweenCamera(0, 5, 12, 0, 1, 0);
    const btn = document.getElementById('camOrbit');
    if (btn) btn.classList.add('active');
  } else if (preset === 'top') {
    tweenCamera(0, 14, 0.1, 0, 0, 0);
    const btn = document.getElementById('camTop');
    if (btn) btn.classList.add('active');
  } else if (preset === 'rack1') {
    tweenCamera(-3.8, 2, 4.5, -3.8, 1.5, 0);
    const btn = document.getElementById('camRack1');
    if (btn) btn.classList.add('active');
  } else if (preset === 'hydro') {
    tweenCamera(3.8, 2, 4.5, 3.8, 1.5, 0);
    const btn = document.getElementById('camHydro');
    if (btn) btn.classList.add('active');
  }
}

function tweenCamera(x, y, z, tx, ty, tz) {
  camera.position.set(x, y, z);
  controls.target.set(tx, ty, tz);
}

// 8. SWITCH CROP TYPE
function switchCropType(type) {
  playTone(520, 'triangle', 0.08);
  cropType = type;
  document.getElementById('tabMush').classList.toggle('active', type === 'mushroom');
  document.getElementById('tabHydro').classList.toggle('active', type === 'hydro');

  build3DRacks();

  if (type === 'mushroom') {
    updateTelemetry(845, 92, 22.4, 1420);
    speakSaarthi("Switched to Oyster Mushroom Darkroom profile. CO2 and fresh air loops active.");
  } else {
    updateTelemetry(1100, 68, 20.8, 1100);
    speakSaarthi("Switched to Hydroponic Basil Container profile. Water nutrient loops engaged.");
  }

  // Notify Spring Boot Backend
  sendActuationToSpring('CROP_PROFILE', 'SWITCH_CROP', null, null, type);
}

// 9. THERMAL / IR VISION MODE
function setViewMode(mode) {
  playTone(640, 'sawtooth', 0.08);
  currentViewMode = mode;
  document.getElementById('btnMode3D').classList.toggle('active', mode === 'normal');
  document.getElementById('btnModeThermal').classList.toggle('active', mode === 'thermal');

  if (mode === 'thermal') {
    scene.background = new THREE.Color(0x020008);
    scene.fog.color = new THREE.Color(0x020008);
  } else {
    scene.background = null;
    scene.fog.color = new THREE.Color(0x05080C);
  }

  build3DRacks();
}

// 10. INTERACTIVE TELEMETRY SLIDERS & BUFFER UPDATE
function onSliderChange(metric, val) {
  val = parseFloat(val);
  if (metric === 'co2') {
    telemetry.co2 = val;
    document.getElementById('sliderCO2Val').textContent = `${Math.round(val)} ppm`;
  } else if (metric === 'rh') {
    telemetry.rh = val;
    document.getElementById('sliderRHVal').textContent = `${Math.round(val)} %`;
  } else if (metric === 'temp') {
    telemetry.temp = val;
    document.getElementById('sliderTempVal').textContent = `${val.toFixed(1)} °C`;
  }
  syncHUD();

  // Push to Java Spring Backend
  pushTelemetryToSpring();
}

function updateTelemetry(co2, rh, temp, fan) {
  telemetry.co2 = co2;
  telemetry.rh = rh;
  telemetry.temp = temp;
  telemetry.fanRpm = fan;

  // Append to Rolling History Buffers
  pushHistory('co2', co2);
  pushHistory('rh', rh);
  pushHistory('temp', temp);
  pushHistory('fan', fan);

  document.getElementById('sliderCO2').value = co2;
  document.getElementById('sliderCO2Val').textContent = `${Math.round(co2)} ppm`;
  document.getElementById('sliderRH').value = rh;
  document.getElementById('sliderRHVal').textContent = `${Math.round(rh)} %`;
  document.getElementById('sliderTemp').value = temp;
  document.getElementById('sliderTempVal').textContent = `${temp.toFixed(1)} °C`;

  syncHUD();
  updateAllSparklines();
}

function pushHistory(metric, val) {
  if (!history[metric]) history[metric] = [];
  history[metric].push(val);
  if (history[metric].length > 20) {
    history[metric].shift();
  }
}

function syncHUD() {
  document.getElementById('hudCO2Val').textContent = Math.round(telemetry.co2);
  document.getElementById('hudRHVal').textContent = Math.round(telemetry.rh);
  document.getElementById('hudTempVal').textContent = telemetry.temp.toFixed(1);
  document.getElementById('hudFanVal').textContent = `${telemetry.fanRpm}`;

  // Evaluate Thresholds with WCAG 2.1 Geometric Shape & Contrast Encoding
  const isCO2Danger = telemetry.co2 > 1300;
  const statCO2 = document.getElementById('hudCO2Status');
  if (statCO2) {
    if (isCO2Danger) {
      statCO2.className = 'm-status-pill danger';
      statCO2.textContent = '▲ SPIKE';
      document.getElementById('masterStatusCapsule').style.borderColor = '#FF3366';
      document.getElementById('masterStatusText').innerHTML = 'STATUS: <strong style="color:#FF3366">▲ WARNING (CO2 SPIKE)</strong>';
    } else {
      statCO2.className = 'm-status-pill ok';
      statCO2.textContent = '● OPTIMAL';
      document.getElementById('masterStatusCapsule').style.borderColor = 'rgba(0, 245, 160, 0.3)';
      document.getElementById('masterStatusText').innerHTML = 'SPRING AUTONOMOUS LOOP: <strong>● ONLINE</strong>';
    }
  }

  const statRH = document.getElementById('hudRHStatus');
  if (statRH) {
    const isRHLow = telemetry.rh < 75;
    if (isRHLow) {
      statRH.className = 'm-status-pill warning';
      statRH.textContent = '▲ LOW RH';
    } else {
      statRH.className = 'm-status-pill ok';
      statRH.textContent = '● OPTIMAL';
    }
  }

  const statTemp = document.getElementById('hudTempStatus');
  if (statTemp) {
    const isTempAnomaly = telemetry.temp > 27 || telemetry.temp < 16;
    if (isTempAnomaly) {
      statTemp.className = 'm-status-pill warning';
      statTemp.textContent = '▲ WARNING';
    } else {
      statTemp.className = 'm-status-pill ok';
      statTemp.textContent = '● OPTIMAL';
    }
  }

  const statFan = document.getElementById('hudFanStatus');
  if (statFan) {
    if (telemetry.fanRpm >= 2400) {
      statFan.className = 'm-status-pill warning';
      statFan.textContent = '▲ PURGE';
    } else if (telemetry.fanRpm === 0) {
      statFan.className = 'm-status-pill danger';
      statFan.textContent = '✖ STOPPED';
    } else {
      statFan.className = 'm-status-pill ok';
      statFan.textContent = '● AUTO';
    }
  }
}

// 11. REAL-TIME SVG SPARKLINES GENERATOR
function updateAllSparklines() {
  renderSparkline('sparkAreaCO2', 'sparkLineCO2', history.co2, 400, 2000, 'trendCO2', 'ppm');
  renderSparkline('sparkAreaRH', 'sparkLineRH', history.rh, 40, 100, 'trendRH', '%');
  renderSparkline('sparkAreaTemp', 'sparkLineTemp', history.temp, 10, 40, 'trendTemp', '°C');
  renderSparkline('sparkAreaFan', 'sparkLineFan', history.fan, 0, 3000, 'trendFan', 'RPM');
}

function renderSparkline(areaId, lineId, dataArray, minRange, maxRange, trendId, unit) {
  const areaElem = document.getElementById(areaId);
  const lineElem = document.getElementById(lineId);
  if (!areaElem || !lineElem || !dataArray || dataArray.length < 2) return;

  const width = 160;
  const height = 32;
  const len = dataArray.length;
  const step = width / (len - 1);

  // Normalize Points
  const points = dataArray.map((val, idx) => {
    const normY = Math.max(0, Math.min(1, (val - minRange) / (maxRange - minRange)));
    const y = height - (normY * (height - 8)) - 4;
    const x = idx * step;
    return { x, y };
  });

  // Build Line Path
  let linePath = `M ${points[0].x.toFixed(1)} ${points[0].y.toFixed(1)}`;
  for (let i = 1; i < points.length; i++) {
    linePath += ` L ${points[i].x.toFixed(1)} ${points[i].y.toFixed(1)}`;
  }
  lineElem.setAttribute('d', linePath);

  // Build Area Path
  const areaPath = `${linePath} L ${width} ${height} L 0 ${height} Z`;
  areaElem.setAttribute('d', areaPath);

  // Calculate Trend Delta
  if (trendId) {
    const trendElem = document.getElementById(trendId);
    if (trendElem && len >= 4) {
      const delta = dataArray[len - 1] - dataArray[len - 4];
      if (Math.abs(delta) < 0.2) {
        trendElem.textContent = '● STABLE';
        trendElem.style.color = 'var(--text-muted)';
      } else if (delta > 0) {
        trendElem.textContent = `▲ +${delta.toFixed(1)} ${unit}`;
        trendElem.style.color = 'var(--neon-emerald)';
      } else {
        trendElem.textContent = `▼ ${delta.toFixed(1)} ${unit}`;
        trendElem.style.color = 'var(--neon-cyan)';
      }
    }
  }
}

// 12. PROCEDURAL HOLOGRAPHIC FACE AVATAR & EMOTION ENGINE (EYES & LIP-SYNC MOUTH)
function initAvatarFaceCanvas() {
  avatarCanvas = document.getElementById('avatarFaceCanvas');
  if (avatarCanvas) {
    avatarCtx = avatarCanvas.getContext('2d');
    // Enable mouse tracking for gaze
    avatarCanvas.addEventListener('mousemove', (e) => {
      const rect = avatarCanvas.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width;
      const y = (e.clientY - rect.top) / rect.height;
      targetGazeX = (x - 0.5) * 2;
      targetGazeY = (y - 0.5) * 2;
    });

    avatarCanvas.addEventListener('mouseleave', () => {
      targetGazeX = 0;
      targetGazeY = 0;
    });
  }
}

function setAvatarEmotion(emotion) {
  playTone(560, 'sine', 0.06);
  currentEmotion = emotion;

  const container = document.getElementById('avatarContainer');
  const badge = document.getElementById('avatarEmotionBadge');

  if (container) {
    container.classList.remove('helpful', 'serious', 'thinking', 'happy');
    container.classList.add(emotion);
  }

  if (badge) {
    badge.className = `avatar-emotion-badge ${emotion}`;
    if (emotion === 'helpful') badge.textContent = '🟢 MOOD: HELPFUL';
    else if (emotion === 'serious') badge.textContent = '🔴 MOOD: ALERT (SERIOUS)';
    else if (emotion === 'thinking') badge.textContent = '🔵 MOOD: THINKING';
    else if (emotion === 'happy') badge.textContent = '✨ MOOD: HAPPY';
  }

  document.querySelectorAll('.btn-emo-pill').forEach(b => {
    b.classList.toggle('active', b.textContent.toLowerCase().includes(emotion));
  });
}

function drawAvatarFace() {
  if (!avatarCtx || !avatarCanvas) return;

  const w = avatarCanvas.width;
  const h = avatarCanvas.height;
  avatarCtx.clearRect(0, 0, w, h);

  speechWavePhase += 0.05;

  // 1. Natural Blinking & Saccadic Gaze Interpolation
  blinkTimer += 0.016;
  if (!isBlinking && blinkTimer > (currentEmotion === 'serious' ? 2.2 : 3.8)) {
    isBlinking = true;
    eyeBlinkProgress = 0;
    blinkTimer = 0;
  }

  if (isBlinking) {
    eyeBlinkProgress += 0.12;
    if (eyeBlinkProgress >= 1) {
      isBlinking = false;
      eyeBlinkProgress = 0;
      // Ambient saccade jump
      targetGazeX = (Math.random() - 0.5) * 0.8;
      targetGazeY = (Math.random() - 0.5) * 0.5;
    }
  }

  // Smooth Gaze Lerp
  eyeGazeX += (targetGazeX - eyeGazeX) * 0.08;
  eyeGazeY += (targetGazeY - eyeGazeY) * 0.08;

  // 2. Select Emotion Palette
  let primaryGlow = '#00F5A0';
  let secondaryGlow = '#00D2FF';
  let irisColor = '#00F5A0';

  if (currentEmotion === 'serious') {
    primaryGlow = '#FF3366';
    secondaryGlow = '#FFB800';
    irisColor = '#FF3366';
  } else if (currentEmotion === 'thinking') {
    primaryGlow = '#00D2FF';
    secondaryGlow = '#3A86FF';
    irisColor = '#00D2FF';
  } else if (currentEmotion === 'happy') {
    primaryGlow = '#9D4EDD';
    secondaryGlow = '#00F5A0';
    irisColor = '#E2CAFF';
  }

  // 3. Draw Eyes
  const leftEyeCenter = { x: 75, y: 55 };
  const rightEyeCenter = { x: 165, y: 55 };

  drawCyberEye(leftEyeCenter.x, leftEyeCenter.y, -1, primaryGlow, secondaryGlow, irisColor);
  drawCyberEye(rightEyeCenter.x, rightEyeCenter.y, 1, primaryGlow, secondaryGlow, irisColor);

  // 4. Draw Audio-Reactive Mouth
  drawCyberMouth(w / 2, 118, primaryGlow, secondaryGlow);
}

function drawCyberEye(cx, cy, side, primaryGlow, secondaryGlow, irisColor) {
  avatarCtx.save();

  // Eye Brow / Cyber Bracket
  avatarCtx.strokeStyle = secondaryGlow;
  avatarCtx.lineWidth = 2.5;
  avatarCtx.lineCap = 'round';
  avatarCtx.shadowColor = secondaryGlow;
  avatarCtx.shadowBlur = 10;

  const browTilt = currentEmotion === 'serious' ? (side * 6) : (currentEmotion === 'thinking' ? -3 : 0);

  avatarCtx.beginPath();
  avatarCtx.moveTo(cx - 26, cy - 24 + browTilt);
  avatarCtx.lineTo(cx + 26, cy - 24 - browTilt);
  avatarCtx.stroke();

  // Eye Sclera / Contour with Blink Scaling
  const openRatio = Math.max(0.08, 1 - Math.sin(eyeBlinkProgress * Math.PI));
  const eyeRadiusX = 24;
  const eyeRadiusY = 14 * openRatio;

  // Outer Eye Aura Glow
  avatarCtx.beginPath();
  avatarCtx.ellipse(cx, cy, eyeRadiusX + 4, eyeRadiusY + 4, 0, 0, Math.PI * 2);
  avatarCtx.strokeStyle = 'rgba(255,255,255,0.08)';
  avatarCtx.lineWidth = 1;
  avatarCtx.stroke();

  // Eye Shape Path
  avatarCtx.beginPath();
  avatarCtx.ellipse(cx, cy, eyeRadiusX, eyeRadiusY, 0, 0, Math.PI * 2);
  avatarCtx.strokeStyle = primaryGlow;
  avatarCtx.lineWidth = 2.2;
  avatarCtx.shadowColor = primaryGlow;
  avatarCtx.shadowBlur = 14;
  avatarCtx.stroke();

  // Clip to Eye Interior for Pupils
  avatarCtx.clip();

  // Pupil & Iris Gaze Position
  const pupilX = cx + (eyeGazeX * 9);
  const pupilY = cy + (eyeGazeY * 5);
  const pupilRadius = (currentEmotion === 'thinking' ? 9 : 7) * openRatio;

  // Iris Gradient
  const irisGrad = avatarCtx.createRadialGradient(pupilX, pupilY, 2, pupilX, pupilY, pupilRadius + 3);
  irisGrad.addColorStop(0, '#FFFFFF');
  irisGrad.addColorStop(0.4, irisColor);
  irisGrad.addColorStop(1, 'transparent');

  avatarCtx.beginPath();
  avatarCtx.arc(pupilX, pupilY, pupilRadius, 0, Math.PI * 2);
  avatarCtx.fillStyle = irisGrad;
  avatarCtx.shadowColor = irisColor;
  avatarCtx.shadowBlur = 15;
  avatarCtx.fill();

  // Thinking Reticle Ring around Pupil
  if (currentEmotion === 'thinking') {
    avatarCtx.beginPath();
    avatarCtx.arc(pupilX, pupilY, pupilRadius + 4, speechWavePhase, speechWavePhase + Math.PI * 1.4);
    avatarCtx.strokeStyle = 'rgba(0, 210, 255, 0.8)';
    avatarCtx.lineWidth = 1.5;
    avatarCtx.stroke();
  }

  avatarCtx.restore();
}

function drawCyberMouth(cx, cy, primaryGlow, secondaryGlow) {
  avatarCtx.save();

  const mouthWidth = 55;
  let mouthHeight = 2;

  // Determine dynamic speech amplitude
  if (currentOrbState === 'speaking') {
    mouthHeight = Math.abs(Math.sin(speechWavePhase * 7)) * 14 + Math.sin(speechWavePhase * 3) * 4 + 4;
  } else if (currentOrbState === 'listening') {
    mouthHeight = Math.abs(Math.sin(speechWavePhase * 4)) * 5 + 2;
  } else if (currentEmotion === 'happy') {
    mouthHeight = 5;
  }

  avatarCtx.strokeStyle = primaryGlow;
  avatarCtx.lineWidth = 2.4;
  avatarCtx.shadowColor = primaryGlow;
  avatarCtx.shadowBlur = 12;
  avatarCtx.lineCap = 'round';

  if (mouthHeight > 4) {
    // Open Speaking Viseme Aperture
    avatarCtx.beginPath();
    avatarCtx.ellipse(cx, cy, mouthWidth / 2, mouthHeight, 0, 0, Math.PI * 2);
    avatarCtx.fillStyle = 'rgba(5, 12, 18, 0.9)';
    avatarCtx.fill();
    avatarCtx.stroke();

    // Inner Energy Viseme Wave
    avatarCtx.beginPath();
    avatarCtx.moveTo(cx - mouthWidth / 2 + 6, cy);
    avatarCtx.quadraticCurveTo(cx, cy + Math.sin(speechWavePhase * 9) * (mouthHeight * 0.6), cx + mouthWidth / 2 - 6, cy);
    avatarCtx.strokeStyle = secondaryGlow;
    avatarCtx.lineWidth = 1.8;
    avatarCtx.stroke();
  } else {
    // Closed / Calm Smiling Baseline
    const curveY = currentEmotion === 'happy' ? -4 : (currentEmotion === 'serious' ? 3 : -1);
    avatarCtx.beginPath();
    avatarCtx.moveTo(cx - mouthWidth / 2, cy);
    avatarCtx.quadraticCurveTo(cx, cy + curveY, cx + mouthWidth / 2, cy);
    avatarCtx.stroke();
  }

  avatarCtx.restore();
}

// 12B. CROP VISION DIAGNOSTIC STUDIO ENGINE
function openVisionModal() {
  playTone(680, 'sine', 0.08);
  const modal = document.getElementById('visionModal');
  if (modal) modal.classList.add('active');
  setAvatarEmotion('thinking');
}

function closeVisionModal() {
  playTone(420, 'sine', 0.06);
  const modal = document.getElementById('visionModal');
  if (modal) modal.classList.remove('active');
  setAvatarEmotion('helpful');
}

function closeVisionModalOnBackdrop(e) {
  if (e.target.id === 'visionModal') closeVisionModal();
}

function runVisionScan(type) {
  playTone(740, 'triangle', 0.08);
  document.querySelectorAll('.btn-pathogen').forEach(b => b.classList.remove('active'));

  const img = document.getElementById('visionScanImage');
  const title = document.getElementById('visionDiagnosisTitle');
  const desc = document.getElementById('visionDiagnosisDesc');
  const directive = document.getElementById('visionDirectiveText');
  const confidence = document.getElementById('visionConfidencePill');
  const reticle = document.getElementById('targetLabel');

  if (type === 'healthy') {
    img.src = "https://image.pollinations.ai/prompt/healthy_oyster_mushroom_fruiting_cluster_clean_white_caps_darkroom?width=512&height=512&nologo=true";
    title.textContent = "Oyster Mushroom (Pleurotus ostreatus) - Optimal Health";
    desc.textContent = "Cap morphology shows healthy convex margins. Zero spore contamination or bacterial blotch detected.";
    directive.textContent = "Maintain current FAE blower duty at 45% (1,420 RPM). Schedule harvest in 18 to 24 hours.";
    confidence.textContent = "🎯 CONFIDENCE: 99.2%";
    reticle.textContent = "STATUS: OPTIMAL CANOPY";
    setAvatarEmotion('happy');
  } else if (type === 'trichoderma') {
    img.src = "https://image.pollinations.ai/prompt/oyster_mushroom_bag_with_green_trichoderma_mold_patch_close_up?width=512&height=512&nologo=true";
    title.textContent = "⚠️ Pathogen Alert: Trichoderma Harzianum (Green Mold)";
    desc.textContent = "Active sporulating green mold patches identified on sawdust substrate. High risk of airborne contamination to adjacent bags.";
    directive.textContent = "Isolate Rack 1 immediately. Spot-treat perimeter with 3% food-grade Hydrogen Peroxide (H2O2) and ramp exhaust ventilation to 2,400 RPM.";
    confidence.textContent = "🎯 CONFIDENCE: 97.8%";
    reticle.textContent = "DETECTED: TRICHODERMA SPORES";
    setAvatarEmotion('serious');
    speakSaarthi("Warning: Trichoderma green mold identified on substrate. Isolate chamber and activate purge exhaust.");
  } else if (type === 'stipe') {
    img.src = "https://image.pollinations.ai/prompt/oyster_mushroom_with_long_stretched_stems_and_tiny_caps_co2_suffocation?width=512&height=512&nologo=true";
    title.textContent = "⚠️ Physiological Disorder: Stipe Elongation (CO2 Suffocation)";
    desc.textContent = "Elongated thick stems with underdeveloped miniature caps. High CO2 levels (>1,200 ppm) during pinhead transition.";
    directive.textContent = "Execute 15-minute continuous FAE cycle. Lower CO2 setpoint to 800 ppm to encourage wide cap formation.";
    confidence.textContent = "🎯 CONFIDENCE: 96.4%";
    reticle.textContent = "DETECTED: STIPE ELONGATION";
    setAvatarEmotion('serious');
  } else if (type === 'tipburn') {
    img.src = "https://image.pollinations.ai/prompt/hydroponic_basil_leaves_with_brown_necrotic_tip_burn_edges?width=512&height=512&nologo=true";
    title.textContent = "⚠️ Nutrient Disorder: Basil / Lettuce Tip Burn";
    desc.textContent = "Marginal necrosis and browning on young inner leaf tips caused by localized calcium deficiency under low transpiration.";
    directive.textContent = "Increase canopy airflow velocity by 25%. Reduce nutrient solution EC by 0.2 mS/cm and verify day/night humidity delta.";
    confidence.textContent = "🎯 CONFIDENCE: 98.1%";
    reticle.textContent = "DETECTED: CALCIUM DEFICIENCY";
    setAvatarEmotion('serious');
  }
}

function triggerAiVisionAnalysis() {
  playTone(880, 'sine', 0.12);
  const reticle = document.getElementById('targetLabel');
  reticle.textContent = 'RUNNING AI MULTIMODAL INFERENCE...';
  setAvatarEmotion('thinking');

  setTimeout(() => {
    reticle.textContent = 'CANOPY ANALYSIS COMPLETE';
    setAvatarEmotion('helpful');
    speakSaarthi("Canopy optical scan complete. Diagnostic telemetry synchronized with crop profile.");
  }, 1200);
}

function applyVisionRecommendation() {
  playTone(920, 'triangle', 0.15);
  sendActuationToSpring('FAN_01', 'VENTILATE', 600, 2400, null);
  telemetry.fanRpm = 2400;
  syncHUD();
  closeVisionModal();
  speakSaarthi("Executing automated agronomy directive. Fresh air ventilation ramped to 2,400 RPM.");
}

// 13. COLLAPSIBLE HUD PANELS & FOCUS MODE
function togglePanel(side) {
  playTone(600, 'sine', 0.05);
  if (side === 'left') {
    const panel = document.getElementById('panelLeft');
    const btn = document.getElementById('btnToggleLeft');
    if (panel && btn) {
      const isCollapsed = panel.classList.toggle('collapsed');
      btn.querySelector('.dock-arrow').textContent = isCollapsed ? '▶' : '◀';
    }
  } else if (side === 'right') {
    const panel = document.getElementById('panelRight');
    const btn = document.getElementById('btnToggleRight');
    if (panel && btn) {
      const isCollapsed = panel.classList.toggle('collapsed');
      btn.querySelector('.dock-arrow').textContent = isCollapsed ? '◀' : '▶';
    }
  }
}

function toggleHudFocus() {
  playTone(720, 'sawtooth', 0.08);
  const wrapper = document.getElementById('hudOverlayWrapper');
  const banner = document.getElementById('focusModeBanner');
  const btn = document.getElementById('btnHudFocus');
  if (wrapper && btn) {
    isHudFocused = !isHudFocused;
    wrapper.classList.toggle('hud-hidden', isHudFocused);
    btn.classList.toggle('active', isHudFocused);
    btn.textContent = isHudFocused ? '👁️ Restore HUD' : '👁️ Focus';
    if (banner) banner.style.display = isHudFocused ? 'block' : 'none';
    if (isHudFocused) {
      showToast('Immersion Mode Active', 'HUD minimized. Click banner or press [H] to restore.', 'info');
    } else {
      showToast('HUD Restored', 'Full spatial controls active.', 'info');
    }
  }
}

// 14. KEYBOARD SHORTCUTS ENGINE
function initKeyboardShortcuts() {
  window.addEventListener('keydown', (e) => {
    // Prevent interfering with text inputs
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.tagName === 'SELECT') {
      return;
    }

    const key = e.key.toUpperCase();
    if (key === '1') setCameraView('orbit');
    else if (key === '2') setCameraView('top');
    else if (key === '3') setCameraView('rack1');
    else if (key === '4') setCameraView('hydro');
    else if (key === 'T') setViewMode(currentViewMode === 'normal' ? 'thermal' : 'normal');
    else if (key === 'H') toggleHudFocus();
    else if (key === '[') togglePanel('left');
    else if (key === ']') togglePanel('right');
    else if (key === 'M') toggleRealMic();
    else if (key === 'S') simulateBreathSpike();
    else if (key === 'R') resetToOptimal();
    else if (key === 'X') emergencyStopFan();
  });
}

function emergencyStopFan() {
  playTone(150, 'sawtooth', 0.4);
  updateTelemetry(telemetry.co2, telemetry.rh, telemetry.temp, 0);
  sendActuationToSpring('FAN_01', 'RELAY_OFF', null, 0, cropType);
  showToast('EMERGENCY OVERRIDE', 'Exhaust blower shut down immediately [X].', 'warning');
}

// 15. SIMULATE BREATH TEST SPIKE
function simulateBreathSpike() {
  playTone(220, 'sawtooth', 0.3);
  updateTelemetry(1520, 94, 23.2, 2800);
  speakSaarthi("Warning: Rapid CO2 breath spike detected at 1,520 ppm. Autonomous fresh air ventilation activated.");
  sendActuationToSpring('SIMULATION', 'SPIKE', null, null, null);
  showToast('Climate Anomaly', 'CO2 spiked to 1,520 ppm. FAE ventilation auto-ramped.', 'warning');
}

function resetToOptimal() {
  playTone(440, 'sine', 0.1);
  updateTelemetry(845, 92, 22.4, 1420);
  speakSaarthi("Chamber parameters normalized to baseline. Systems optimal.");
  sendActuationToSpring('SIMULATION', 'RESET', null, null, null);
  showToast('Chamber Reset', 'Parameters returned to optimal baseline.', 'success');
}

// 16. SPRING BOOT WEBSOCKET & REST RESOLUTION
function getApiBaseUrl() {
  if (window.location.protocol === 'file:' || !window.location.host) {
    return 'http://localhost:8080';
  }
  return '';
}

function getWebSocketUrl() {
  if (window.location.protocol === 'file:' || !window.location.host) {
    return 'ws://localhost:8080/ws/telemetry';
  }
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${wsProtocol}//${window.location.host}/ws/telemetry`;
}

let wsReconnectAttempts = 0;
const WS_MAX_RECONNECT_ATTEMPTS = 10;

function initSpringWebSocket() {
  let wsUrl = getWebSocketUrl();
  const token = aiBrainConfig.operatorToken || sessionStorage.getItem('saarthi_operator_token');
  if (token) {
    wsUrl += (wsUrl.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token);
  }

  try {
    telemetrySocket = new WebSocket(wsUrl);

    telemetrySocket.onopen = () => {
      wsReconnectAttempts = 0;
      console.log("🌐 Connected to Spring Boot Telemetry WebSocket:", wsUrl);
      const dot = document.getElementById('masterStatusCapsule');
      if (dot) dot.style.borderColor = 'rgba(0, 245, 160, 0.5)';
    };

    telemetrySocket.onmessage = (event) => {
      try {
        const record = JSON.parse(event.data);
        if (record && record.co2Ppm !== undefined) {
          updateTelemetry(record.co2Ppm, record.humidityRh, record.tempC, record.fanRpm);
          const badge = document.getElementById('envModeBadge');
          const badgeLabel = document.getElementById('envModeLabel');
          if (badge && badgeLabel) {
            if (record.deviceId && record.deviceId.startsWith('ESP32')) {
              badge.className = 'env-mode-badge live';
              badgeLabel.textContent = `🟢 LIVE NODE (${record.deviceId})`;
            } else {
              badge.className = 'env-mode-badge simulated';
              badgeLabel.textContent = '🟡 TESTBENCH SIMULATION';
            }
          }
        }
      } catch (e) {
        console.warn("WebSocket parse error:", e);
      }
    };

    telemetrySocket.onclose = (event) => {
      if (event && event.code === 1008) {
        console.warn("WebSocket closed by server (Policy Violation - Token Required).");
        return;
      }
      if (wsReconnectAttempts < WS_MAX_RECONNECT_ATTEMPTS) {
        const backoffDelay = Math.min(3000 * Math.pow(1.5, wsReconnectAttempts), 30000);
        wsReconnectAttempts++;
        setTimeout(initSpringWebSocket, backoffDelay);
      } else {
        showToast("Connection Lost", "WebSocket disconnected after multiple retries. Refresh page to reconnect.", "warning");
      }
    };

    telemetrySocket.onerror = () => {
      // Standalone mode without backend
    };
  } catch (err) {
    // Graceful offline fallback
  }
}

function sendActuationToSpring(target, action, duration, rpm, crop) {
  fetch(`${getApiBaseUrl()}/api/v1/actuate`, {
    method: 'POST',
    headers: getOperatorHeaders(),
    body: JSON.stringify({ target, action, durationSeconds: duration, rpm, crop, reason: 'CLIENT_UI_ACTION' })
  }).then(async (res) => {
    if (!res.ok) {
      showToast("Actuation Restricted", "Operator token required to actuate physical relays. Configure in Brain modal.", "error");
    }
  }).catch((err) => {
    console.warn("Actuation error:", err);
  });
}

function pushTelemetryToSpring() {
  const headers = { 'Content-Type': 'application/json' };
  const devToken = sessionStorage.getItem('saarthi_device_token') || '';
  if (devToken) {
    headers['X-Device-Token'] = devToken;
  }

  fetch(`${getApiBaseUrl()}/api/v1/telemetry/push`, {
    method: 'POST',
    headers: headers,
    body: JSON.stringify({
      deviceId: 'SAARTHI_WEB_HUD',
      co2Ppm: telemetry.co2,
      humidityRh: telemetry.rh,
      tempC: telemetry.temp,
      fanRpm: telemetry.fanRpm,
      cropType: cropType
    })
  }).catch(() => {});
}

// 17. BRAIN CONFIGURATION & STATUS MANAGEMENT
function initBrainConfig() {
  const dot = document.getElementById('brainDot');
  const statusText = document.getElementById('brainStatusText');
  const aiTagText = document.getElementById('aiTagText');
  const aiDot = document.getElementById('aiDot');

  // API-key inputs are removed: provider keys live server-side only.
  const modelSelect = document.getElementById('aiProviderSelect');
  const voiceSelect = document.getElementById('voiceEngineSelect');

  if (modelSelect) modelSelect.value = aiBrainConfig.model;
  if (voiceSelect) voiceSelect.value = aiBrainConfig.voiceEngine;

  if (dot) dot.className = 'brain-dot connected';
  if (statusText) {
    let name = 'GROQ AI';
    if (aiBrainConfig.model.includes('gpt')) name = 'GPT-OSS 120B';
    else if (aiBrainConfig.model.includes('qwen')) name = 'QWEN 3.8';
    else if (aiBrainConfig.model.includes('gemini')) name = 'GEMINI 3.6';
    else if (aiBrainConfig.model.includes('deepseek')) name = 'DEEPSEEK-R1';
    statusText.textContent = `⚡ AI: ${name}`;
  }
  if (aiTagText) aiTagText.textContent = aiBrainConfig.voiceEngine === 'elevenlabs' ? 'STUDIO AI VOICE' : 'SPRING AI LIVE';
  if (aiDot) aiDot.className = 'dot-green';
}

function openBrainModal() {
  playTone(580, 'sine', 0.08);
  const modal = document.getElementById('brainModal');
  if (modal) modal.classList.add('active');
  initBrainConfig();
}

function closeBrainModal() {
  playTone(420, 'sine', 0.06);
  const modal = document.getElementById('brainModal');
  if (modal) modal.classList.remove('active');
}

function closeBrainModalOnBackdrop(e) {
  if (e.target.id === 'brainModal') closeBrainModal();
}

function clearOperatorToken() {
  aiBrainConfig.operatorToken = '';
  sessionStorage.removeItem('saarthi_operator_token');
  const input = document.getElementById('saarthiOperatorTokenInput');
  if (input) input.value = '';
  initBrainConfig();
  const feedback = document.getElementById('testFeedbackBox');
  if (feedback) feedback.style.display = 'none';
  playTone(300, 'sine', 0.1);
}

function saveGeminiSettings() {
  const model = document.getElementById('aiProviderSelect').value;
  const voiceEngine = document.getElementById('voiceEngineSelect').value;
  const tokenInput = document.getElementById('saarthiOperatorTokenInput');
  const token = (tokenInput && tokenInput.value.trim()) || aiBrainConfig.operatorToken;

  aiBrainConfig.model = model;
  aiBrainConfig.voiceEngine = voiceEngine;
  if (token) {
    aiBrainConfig.operatorToken = token;
    sessionStorage.setItem('saarthi_operator_token', token);
  }

  localStorage.setItem('saarthi_ai_model', model);
  localStorage.setItem('saarthi_voice_engine', voiceEngine);

  initBrainConfig();
  closeBrainModal();
  playTone(880, 'triangle', 0.15);

  speakSaarthi(`AI Studio engaged with ${model.includes('deepseek') ? 'DeepSeek-R1 reasoning' : 'Gemini 2.0'} and ${voiceEngine === 'elevenlabs' ? 'ElevenLabs studio voice' : 'browser speech'}. Ready for agronomy operations.`);
}

async function testGeminiConnection() {
  const model = document.getElementById('aiProviderSelect').value;
  const feedback = document.getElementById('testFeedbackBox');
  const icon = document.getElementById('testFeedbackIcon');
  const text = document.getElementById('testFeedbackText');

  feedback.style.display = 'flex';
  feedback.className = 'test-feedback-box';
  icon.textContent = '⏳';
  text.textContent = 'Testing multi-engine pipeline through Java Spring AI Core...';

  try {
    const res = await fetch(`${getApiBaseUrl()}/api/v1/ai/chat`, {
      method: 'POST',
      headers: getOperatorHeaders(),
      body: JSON.stringify({
        query: "Connection test. Verify status.",
        model: model,
        language: aiBrainConfig.language
      })
    });

    const data = await res.json();
    if (res.ok && data.success) {
      feedback.className = 'test-feedback-box success';
      icon.textContent = '✅';
      text.textContent = `Spring AI Pipeline Verified! Active Model: ${data.modelUsed || model}.`;
      playTone(920, 'sine', 0.15);
    } else {
      throw new Error(data.errorMessage || 'API Error');
    }
  } catch (err) {
    feedback.className = 'test-feedback-box error';
    icon.textContent = '❌';
    text.textContent = `Notice: ${err.message || 'Could not verify AI service.'}`;
  }
}

// 18. AUDIO ORB STATE MACHINE (Visual Feedback)
function setOrbState(state) {
  currentOrbState = state;
  const container = document.getElementById('orbDisplayContainer');
  const label = document.getElementById('orbStateLabel');
  if (!container || !label) return;

  container.classList.remove('listening', 'thinking', 'speaking');

  if (state === 'listening') {
    container.classList.add('listening');
    label.textContent = 'AI LISTENING...';
    label.style.color = '#00D2FF';
    label.style.borderColor = 'rgba(0, 210, 255, 0.5)';
  } else if (state === 'thinking') {
    container.classList.add('thinking');
    label.textContent = 'AI THINKING (R1)...';
    label.style.color = '#FFB800';
    label.style.borderColor = 'rgba(255, 184, 0, 0.5)';
  } else if (state === 'speaking') {
    container.classList.add('speaking');
    label.textContent = 'AI SPEAKING...';
    label.style.color = '#00F5A0';
    label.style.borderColor = 'rgba(0, 245, 160, 0.5)';
  } else {
    label.textContent = 'AI IDLE';
    label.style.color = 'var(--neon-cyan)';
    label.style.borderColor = 'rgba(0, 210, 255, 0.2)';
  }
}

// 19. REAL SPEECH RECOGNITION (LIVE MIC)
function initSpeechRecognition() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (SpeechRecognition) {
    recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.lang = aiBrainConfig.language || 'en-US';
    recognition.interimResults = false;

    recognition.onstart = () => {
      isRecordingMic = true;
      document.getElementById('btnMicRecord').classList.add('recording');
      document.getElementById('btnMicText').textContent = 'Listening... Speak Now!';
      setOrbState('listening');
      playTone(600, 'sine', 0.1);
    };

    recognition.onresult = (event) => {
      const transcript = event.results[0][0].transcript;
      stopMic();
      processUserQuery(transcript);
    };

    recognition.onerror = (e) => {
      console.warn('Speech recognition error:', e);
      stopMic();
      setOrbState('idle');
      showToast('Microphone Alert', `Speech input error (${e.error || 'denied'}). You can type queries in the prompt bar.`, 'warning');
    };

    recognition.onend = () => {
      stopMic();
      if (currentOrbState === 'listening') setOrbState('idle');
    };
  }
}

function toggleRealMic() {
  if (!recognition) {
    showToast('Speech Unsupported', 'Web Speech API is supported in Google Chrome, Edge, and Safari.', 'warning');
    return;
  }
  if (!isRecordingMic) {
    try {
      recognition.start();
      showToast('Microphone Active', 'Listening for speech input...', 'info');
    } catch (err) {
      console.warn("Recognition start error:", err);
    }
  } else {
    recognition.stop();
  }
}

function stopMic() {
  isRecordingMic = false;
  const btn = document.getElementById('btnMicRecord');
  if (btn) btn.classList.remove('recording');
  const txt = document.getElementById('btnMicText');
  if (txt) txt.textContent = 'Push to Talk [M]';
}

// 19B. CONTINUOUS AMBIENT "HEY SAARTHI" WAKE-WORD ENGINE
let isWakeWordMode = false;
let wakeWordRecognition = null;

function initWakeWordEngine() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) return;

  wakeWordRecognition = new SpeechRecognition();
  wakeWordRecognition.continuous = true;
  wakeWordRecognition.interimResults = true;
  wakeWordRecognition.lang = aiBrainConfig.language || 'en-US';

  wakeWordRecognition.onresult = (event) => {
    if (!isWakeWordMode || isQueryPending) return;

    for (let i = event.resultIndex; i < event.results.length; i++) {
      const transcript = event.results[i][0].transcript.trim().toLowerCase();
      
      // Check for wake words: "hey saarthi", "ok saarthi", "saarthi"
      if (transcript.includes('saarthi')) {
        const matchIdx = transcript.indexOf('saarthi') + 'saarthi'.length;
        const command = transcript.substring(matchIdx).trim();

        playTone(880, 'sine', 0.12);
        showToast('Wake-Word Detected', 'Saarthi heard you! Processing query...', 'info');

        if (command.length > 2) {
          processUserQuery(command);
        } else {
          speakSaarthi("Yes Operator, I am listening. How can I assist you?");
        }
        break;
      }
    }
  };

  wakeWordRecognition.onerror = (e) => {
    if (isWakeWordMode && e.error !== 'no-speech') {
      console.warn("Wake-word error:", e.error);
    }
  };

  wakeWordRecognition.onend = () => {
    if (isWakeWordMode) {
      setTimeout(() => {
        try {
          if (isWakeWordMode && currentOrbState !== 'speaking' && !isRecordingMic) {
            wakeWordRecognition.start();
          }
        } catch (err) {}
      }, 500);
    }
  };
}

function toggleWakeWordMode() {
  isWakeWordMode = !isWakeWordMode;
  const btn = document.getElementById('btnWakeWordToggle');
  const txt = document.getElementById('wakeWordStatusText');
  const icon = document.getElementById('wakeIcon');

  if (isWakeWordMode) {
    if (!wakeWordRecognition) initWakeWordEngine();
    try {
      wakeWordRecognition.start();
    } catch (e) {}
    if (btn) btn.classList.add('active');
    if (txt) txt.textContent = 'Wake-Word: ON';
    if (icon) icon.textContent = '🟢';
    showToast('Wake-Word Armed', 'Continuous listening active. Say "Hey Saarthi" anytime.', 'success');
    playTone(660, 'sine', 0.08);
  } else {
    try {
      if (wakeWordRecognition) wakeWordRecognition.stop();
    } catch (e) {}
    if (btn) btn.classList.remove('active');
    if (txt) txt.textContent = 'Wake-Word: OFF';
    if (icon) icon.textContent = '👂';
    showToast('Wake-Word Disarmed', 'Continuous listening stopped.', 'info');
    playTone(440, 'sine', 0.08);
  }
}

function handleTextSubmit(event) {
  event.preventDefault();
  const input = document.getElementById('textPromptInput');
  if (!input) return;
  const text = input.value.trim();
  if (!text) return;
  input.value = '';
  processUserQuery(text);
}

let isQueryPending = false;

// 20. MAIN QUERY PROCESSOR
async function processUserQuery(queryText) {
  if (!queryText || isQueryPending) return;
  isQueryPending = true;

  // Cancel prior speech playback to avoid audio collision
  if (currentAudioElement) {
    currentAudioElement.pause();
    currentAudioElement = null;
  }
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel();
  }

  displayUserQuery(queryText);
  setOrbState('thinking');
  playTone(520, 'triangle', 0.1);

  // Try Java Spring Boot REST API Endpoint first (Supports Groq DeepSeek-R1 & Gemini 2.0)
  try {
    if (!ensureOperatorToken()) throw new Error('Operator token required for chat');
    const res = await fetch(`${getApiBaseUrl()}/api/v1/ai/chat`, {
      method: 'POST',
      headers: getOperatorHeaders(),
      body: JSON.stringify({
        query: queryText,
        model: aiBrainConfig.model,
        language: aiBrainConfig.language
      })
    });

    if (res.ok) {
      const data = await res.json();
      if (data.emotion) {
        setAvatarEmotion(data.emotion);
      }
      if (data.executedAction) {
        handleExecutedAction(data.executedAction);
        showToast('Autonomous Actuation', `Triggered ${data.executedAction.action} via AI directive.`, 'info');
      }
      speakSaarthi(data.replyText);
      isQueryPending = false;
      return;
    }
  } catch (backendError) {
    console.warn("Spring Boot backend query failed, falling back:", backendError);
  }

  queryLocalAgronomyEngine(queryText);
  isQueryPending = false;
}

function displayUserQuery(text) {
  document.getElementById('dialogueTime').textContent = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  document.getElementById('saarthiSpeechText').innerHTML = `<em>User asked: "${text}"</em><br><span style="color:#00D2FF;">Thinking with DeepSeek-R1...</span>`;
}

function handleExecutedAction(action) {
  if (!action || !action.action) return;
  const act = action.action.toUpperCase();

  if (act === "VENTILATE" || act === "RELAY_ON") {
    telemetry.fanRpm = action.rpm || 2400;
    telemetry.fanDuty = 80;
    syncHUD();
  } else if (act === "SWITCH_CROP") {
    switchCropType(action.crop === 'hydro' ? 'hydro' : 'mushroom');
  } else if (act === "SPIKE") {
    simulateBreathSpike();
  } else if (act === "RESET") {
    resetToOptimal();
  }
}

// 22. ENHANCED LOCAL AGRONOMY RULE ENGINE
function queryLocalAgronomyEngine(text) {
  const query = text.toLowerCase();
  setOrbState('thinking');

  setTimeout(() => {
    let response = "";
    if (query.includes('green') || query.includes('mold') || query.includes('trichoderma') || query.includes('patch')) {
      response = "Green patches indicate Trichoderma mold. Isolate the affected substrate immediately, spot treat with 3% hydrogen peroxide, and ramp fresh air exhaust.";
    } else if (query.includes('tip burn') || query.includes('necrosis') || query.includes('lettuce') || query.includes('leaf')) {
      response = "Tip burn in greens is caused by calcium deficiency from low transpiration. Increase canopy air velocity and reduce nutrient EC by 0.2 mS/cm.";
    } else if (query.includes('fan') || query.includes('ventilate') || query.includes('air') || query.includes('exhaust')) {
      telemetry.fanRpm = 2400;
      syncHUD();
      response = "Initiating 10-minute exhaust purge cycle. Target CO2 reduction to 750 ppm.";
    } else if (query.includes('status') || query.includes('room') || query.includes('how') || query.includes('report')) {
      response = `Chamber 1 is stable. CO2 is ${Math.round(telemetry.co2)} ppm, humidity is ${Math.round(telemetry.rh)}%, and temperature is ${telemetry.temp.toFixed(1)}°C.`;
    } else if (query.includes('switch') || query.includes('crop') || query.includes('hydro') || query.includes('basil')) {
      switchCropType('hydro');
      response = "Switched active chamber profile to Hydroponic Basil NFT System.";
    } else if (query.includes('harvest') || query.includes('log')) {
      response = "Logged 15.0 kilograms Oyster Mushroom harvest from Rack 1 into database.";
    } else {
      response = `I analyzed "${text}". Chamber 1 is stable at ${Math.round(telemetry.co2)} ppm CO2.`;
    }

    speakSaarthi(response);
  }, 400);
}

// 23. HYBRID SPEECH SYNTHESIS (ELEVENLABS + WEB SPEECH FALLBACK)
async function speakSaarthi(text) {
  document.getElementById('saarthiSpeechText').textContent = `"${text}"`;
  document.getElementById('dialogueTime').textContent = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  // 1. If ElevenLabs voice is active, stream realistic audio via Spring Backend
  if (aiBrainConfig.voiceEngine === 'elevenlabs') {
    try {
      if (currentAudioElement) {
        currentAudioElement.pause();
        currentAudioElement = null;
      }

      setOrbState('thinking');
      if (!ensureOperatorToken()) throw new Error('Operator token required for TTS');
      const res = await fetch(`${getApiBaseUrl()}/api/v1/ai/tts`, {
        method: 'POST',
        headers: getOperatorHeaders(),
        body: JSON.stringify({
          text: text
        })
      });

      if (res.ok) {
        const audioBlob = await res.blob();
        const audioUrl = URL.createObjectURL(audioBlob);
        const audio = new Audio(audioUrl);
        currentAudioElement = audio;

        setOrbState('speaking');

        audio.onended = () => {
          setOrbState('idle');
          URL.revokeObjectURL(audioUrl);
          currentAudioElement = null;
        };

        audio.onerror = () => {
          setOrbState('idle');
          fallbackBrowserTts(text);
        };

        await audio.play();
        return;
      }
    } catch (err) {
      console.warn("ElevenLabs TTS streaming bypassed, falling back to Web Speech:", err);
    }
  }

  // 2. Fallback to Browser Native Web Speech API
  fallbackBrowserTts(text);
}

function fallbackBrowserTts(text) {
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = 1.05;
    utterance.pitch = 1.0;
    utterance.lang = aiBrainConfig.language || 'en-US';

    utterance.onstart = () => {
      setOrbState('speaking');
    };

    utterance.onend = () => {
      setOrbState('idle');
    };

    utterance.onerror = () => {
      setOrbState('idle');
    };

    window.speechSynthesis.speak(utterance);
  } else {
    setOrbState('idle');
  }
}

// 24. QUICK VOICE INTENTS
function triggerVoiceIntent(intent) {
  if (intent === 'status') {
    processUserQuery("Give me a full status report on Chamber 1 parameters and crop health.");
  } else if (intent === 'disease') {
    processUserQuery("Why are there dark green mold patches appearing on my mushroom bags and how do I fix it?");
  } else if (intent === 'ventilate') {
    processUserQuery("Ventilate the chamber for 10 minutes at high fan speed.");
  } else if (intent === 'lettuce_tips') {
    processUserQuery("How do I prevent tip burn and leaf necrosis in hydroponic greens?");
  }
}

// 25. WEB AUDIO SYNTHESIZER SFX
function playTone(freq, type, duration) {
  if (!isAudioFxEnabled) return;
  try {
    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, audioCtx.currentTime);
    gain.gain.setValueAtTime(0.08, audioCtx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + duration);
    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start();
    osc.stop(audioCtx.currentTime + duration);
  } catch (e) {}
}

function toggleAudioFx() {
  isAudioFxEnabled = !isAudioFxEnabled;
  document.getElementById('btnSoundToggle').textContent = isAudioFxEnabled ? '🔊 SFX: ON' : '🔇 SFX: OFF';
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

/* =========================================================================
   OPENJARVIS PERSISTENT CROP LIFECYCLE LEDGER CONTROLLER
   ========================================================================= */
function openLedgerModal() {
  const modal = document.getElementById('ledgerModal');
  if (modal) {
    modal.classList.add('active');
    fetchLedgerTimeline();
  }
}

function closeLedgerModal() {
  const modal = document.getElementById('ledgerModal');
  if (modal) modal.classList.remove('active');
}

function closeLedgerModalOnBackdrop(e) {
  if (e.target && e.target.id === 'ledgerModal') {
    closeLedgerModal();
  }
}

async function fetchLedgerTimeline() {
  const container = document.getElementById('ledgerTimelineList');
  if (!container) return;

  try {
    const res = await fetch('/api/v1/ai/memory/timeline');
    if (!res.ok) throw new Error('Network error loading timeline');
    const timeline = await res.json();

    if (!Array.isArray(timeline) || timeline.length === 0) {
      container.innerHTML = '<div class="timeline-loading">No memory traces recorded yet.</div>';
      return;
    }

    container.innerHTML = timeline.map(event => `
      <div class="timeline-card">
        <span class="timeline-day-pill">DAY ${event.day || '?'}</span>
        <div class="timeline-content">
          <div class="timeline-header-row">
            <span class="timeline-type">${escapeHtml(event.type || 'EVENT')}</span>
            <span class="timeline-time">${escapeHtml(event.timestamp || '')}</span>
          </div>
          <p class="timeline-desc">${escapeHtml(event.description || '')}</p>
        </div>
      </div>
    `).reverse().join('');
  } catch (err) {
    console.error('Error fetching ledger:', err);
    container.innerHTML = '<div class="timeline-loading" style="color:var(--neon-crimson);">Failed to load memory ledger.</div>';
  }
}

async function submitLedgerEvent() {
  const dayInput = document.getElementById('ledgerDayInput');
  const typeSelect = document.getElementById('ledgerTypeSelect');
  const descInput = document.getElementById('ledgerDescInput');

  const day = parseInt(dayInput ? dayInput.value : '1', 10) || 1;
  const type = typeSelect ? typeSelect.value : 'OPERATOR_NOTE';
  const description = descInput && descInput.value.trim() ? descInput.value.trim() : 'Manual observation recorded.';

  try {
    const res = await fetch('/api/v1/ai/memory/log', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ day, type, description, healthStatus: 'OPTIMAL' })
    });

    if (res.ok) {
      if (descInput) descInput.value = '';
      fetchLedgerTimeline();
      playSfx('switch');
    }
  } catch (err) {
    console.error('Error submitting ledger milestone:', err);
  }
}

/* =========================================================================
   ONBOARDING SPOTLIGHT WALKTHROUGH & ADVANCED UX CONTROLLERS
   ========================================================================= */

let currentTourStep = 0;
const tourSteps = [
  {
    stepBadge: "STEP 1 OF 3",
    icon: "🌐",
    title: "Spatial 3D Digital Twin",
    desc: "This is your real-time 3D grow room. Click and drag to orbit around your grow racks, switch perspective camera presets, or toggle Virtual IR thermal mode [T].",
    targetElementId: null,
    btnText: "Explore Controls →"
  },
  {
    stepBadge: "STEP 2 OF 3",
    icon: "🕹️",
    title: "Chamber Telemetry & Overrides",
    desc: "Monitor real-time CO2, humidity, and temperature sparklines on the left. Test climate overrides and breath spikes in safe simulation mode.",
    targetElementId: "panelLeft",
    btnText: "Meet AI Copilot →"
  },
  {
    stepBadge: "STEP 3 OF 3",
    icon: "🤖",
    title: "AI Agronomist Copilot",
    desc: "Chat with Saarthi using live voice or text. Ask for crop pathology diagnoses, nutrient tips, or let Saarthi autonomously ramp exhaust blowers.",
    targetElementId: "panelRight",
    btnText: "Get Started ✨"
  }
];

function startOnboardingTour(force = false) {
  if (!force && localStorage.getItem('saarthi_tour_completed') === 'true') return;
  currentTourStep = 0;
  const overlay = document.getElementById('onboardingOverlay');
  if (overlay) {
    overlay.style.display = 'flex';
    renderTourStep();
    playTone(540, 'sine', 0.1);
  }
}

function renderTourStep() {
  const step = tourSteps[currentTourStep];
  if (!step) return;

  const badge = document.getElementById('tourStepBadge');
  const icon = document.getElementById('tourIconRow');
  const title = document.getElementById('tourTitle');
  const desc = document.getElementById('tourDesc');
  const btnNext = document.getElementById('btnTourNext');
  const btnPrev = document.getElementById('btnTourPrev');

  if (badge) badge.textContent = step.stepBadge;
  if (icon) icon.textContent = step.icon;
  if (title) title.textContent = step.title;
  if (desc) desc.textContent = step.desc;
  if (btnNext) btnNext.textContent = step.btnText;
  if (btnPrev) btnPrev.style.display = currentTourStep > 0 ? 'inline-block' : 'none';

  // Update dots
  for (let i = 0; i < tourSteps.length; i++) {
    const dot = document.getElementById(`tourDot${i}`);
    if (dot) {
      if (i === currentTourStep) dot.classList.add('active');
      else dot.classList.remove('active');
    }
  }

  // Clear previous spotlight classes
  document.querySelectorAll('.tour-spotlight-active').forEach(el => el.classList.remove('tour-spotlight-active'));

  // Spotlight active element (excluding canvas)
  if (step.targetElementId && step.targetElementId !== 'canvas3d-container') {
    const target = document.getElementById(step.targetElementId);
    if (target) {
      target.classList.add('tour-spotlight-active');
      if (target.classList.contains('collapsed')) {
        target.classList.remove('collapsed');
        target.style.display = 'flex';
      }
    }
  }
}

function nextTourStep() {
  if (currentTourStep < tourSteps.length - 1) {
    currentTourStep++;
    renderTourStep();
    playTone(600, 'sine', 0.08);
  } else {
    skipTour();
    showToast('Tour Completed', 'Welcome aboard! Click "Test Spike" or ask Saarthi a question to begin.', 'success');
  }
}

function prevTourStep() {
  if (currentTourStep > 0) {
    currentTourStep--;
    renderTourStep();
    playTone(480, 'sine', 0.08);
  }
}

function skipTour() {
  const overlay = document.getElementById('onboardingOverlay');
  if (overlay) overlay.style.display = 'none';
  document.querySelectorAll('.tour-spotlight-active').forEach(el => el.classList.remove('tour-spotlight-active'));
  localStorage.setItem('saarthi_tour_completed', 'true');
  playTone(440, 'triangle', 0.06);
}

/* Floating Camera Reset Anchor */
function resetCameraHome() {
  if (typeof setCameraView === 'function') {
    setCameraView('orbit');
  }
  showToast('Camera Centered', '3D chamber perspective reset to default 360° orbit view.', 'info');
  playTone(440, 'sine', 0.08);
}

/* Toast Notification System */
function showToast(title, message, type = 'info', duration = 3800) {
  const container = document.getElementById('toastContainer');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `saarthi-toast ${type}`;
  const icon = type === 'warning' ? '🚨' : (type === 'success' ? '✅' : 'ℹ️');
  toast.innerHTML = `
    <span class="saarthi-toast-icon">${icon}</span>
    <div class="saarthi-toast-content">
      <div class="saarthi-toast-title">${title}</div>
      <div class="saarthi-toast-msg">${message}</div>
    </div>
  `;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(40px)';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

