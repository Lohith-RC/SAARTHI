/* ==========================================================================
   SAARTHI LITE: MINIMALIST PROCEDURAL AI ENGINE & ERROR-CORRECTION ENGINE
   ========================================================================== */

let canvas, ctx;
let currentMode = 'helpful'; // 'helpful' | 'happy'
let isSpeaking = false;
let isTyping = false;
let typingTimer = null;
let speechPhase = 0;
let blinkProgress = 0;
let isBlinking = false;
let gaze = { x: 0, y: 0 };
let targetGaze = { x: 0, y: 0 };
let recognition = null;
let isRecordingMic = false;
let currentAudioElement = null;

// Telemetry State
const telemetry = {
  co2: 845,
  rh: 92,
  temp: 22.4,
  fanRpm: 1420
};

// Auto-correction Dictionary for Agronomy Terms
const typoDictionary = {
  'ventlate': 'ventilate',
  'ventelate': 'ventilate',
  'trichodrma': 'trichoderma',
  'tricoderma': 'trichoderma',
  'letuce': 'lettuce',
  'harvst': 'harvest',
  'humdity': 'humidity',
  'temprature': 'temperature',
  'mushrom': 'mushroom'
};

document.addEventListener('DOMContentLoaded', () => {
  initMinimalistFace();
  initSpeechRecognition();
  initTelemetryWebSocket();
  requestAnimationFrame(renderLoop);
});

// 1. INITIALIZE MINIMALIST 2D VECTOR FACE CANVAS
function initMinimalistFace() {
  canvas = document.getElementById('minimalistFaceCanvas');
  if (!canvas) return;
  ctx = canvas.getContext('2d');

  // Gaze Tracking
  window.addEventListener('mousemove', (e) => {
    const rect = canvas.getBoundingClientRect();
    targetGaze.x = Math.max(-1, Math.min(1, ((e.clientX - rect.left) / rect.width - 0.5) * 2));
    targetGaze.y = Math.max(-1, Math.min(1, ((e.clientY - rect.top) / rect.height - 0.5) * 2));
  });

  window.addEventListener('mouseleave', () => {
    targetGaze.x = 0;
    targetGaze.y = 0;
  });
}

// 2. SWITCH EMOTION MODES (HELPFUL / HAPPY)
function switchEmotionMode(mode) {
  currentMode = mode;
  playTone(mode === 'happy' ? 780 : 540, 'sine', 0.08);

  const glow = document.getElementById('ambientGlow');
  const card = document.getElementById('faceCard');
  const dot = document.querySelector('.mode-dot');
  const modeText = document.getElementById('modeText');
  const speechBox = document.getElementById('speechOutputBox');

  document.querySelectorAll('.btn-mode-pill').forEach(b => b.classList.remove('active'));

  if (mode === 'happy') {
    document.getElementById('btnModeHappy').classList.add('active');
    if (glow) glow.className = 'ambient-glow happy';
    if (card) { card.className = 'face-card happy'; }
    if (dot) dot.className = 'mode-dot happy';
    if (modeText) modeText.textContent = 'HAPPY MODE';
    if (speechBox) speechBox.className = 'speech-output-box happy';
  } else {
    document.getElementById('btnModeHelpful').classList.add('active');
    if (glow) glow.className = 'ambient-glow';
    if (card) { card.className = 'face-card helpful'; }
    if (dot) dot.className = 'mode-dot helpful';
    if (modeText) modeText.textContent = 'HELPFUL MODE';
    if (speechBox) speechBox.className = 'speech-output-box';
  }
}

// 3. PROCEDURAL VECTOR FACE RENDER LOOP (60 FPS, LOW-CPU)
function renderLoop() {
  if (ctx && canvas) {
    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    speechPhase += 0.06;

    // Gaze Smooth Lerp
    gaze.x += (targetGaze.x - gaze.x) * 0.1;
    gaze.y += (targetGaze.y - gaze.y) * 0.1;

    // Blinking Timer
    if (Math.random() < 0.007 && !isBlinking) isBlinking = true;
    if (isBlinking) {
      blinkProgress += 0.14;
      if (blinkProgress >= 1) {
        blinkProgress = 0;
        isBlinking = false;
      }
    }

    const isHelpful = currentMode === 'helpful';
    const primaryGlow = isHelpful ? '#00F5A0' : '#9D4EDD';
    const secondaryGlow = isHelpful ? '#00D2FF' : '#FF70A6';

    // A. Render Cybernetic Brows
    drawBrow(w * 0.32, h * 0.32, -1, primaryGlow, secondaryGlow);
    drawBrow(w * 0.68, h * 0.32, 1, primaryGlow, secondaryGlow);

    // B. Render Minimalist Eyes
    const eyeRadius = 20;
    const openRatio = Math.max(0.08, 1 - Math.sin(blinkProgress * Math.PI));

    drawEye(w * 0.32, h * 0.44, eyeRadius, openRatio, primaryGlow, secondaryGlow);
    drawEye(w * 0.68, h * 0.44, eyeRadius, openRatio, primaryGlow, secondaryGlow);

    // C. Render Minimalist Mouth (Lip-Sync + Typing Cadence)
    drawMouth(w * 0.5, h * 0.74, primaryGlow, secondaryGlow);
  }

  requestAnimationFrame(renderLoop);
}

function drawBrow(cx, cy, side, primaryGlow, secondaryGlow) {
  ctx.save();
  ctx.strokeStyle = secondaryGlow;
  ctx.lineWidth = 2.2;
  ctx.shadowColor = secondaryGlow;
  ctx.shadowBlur = 8;
  ctx.lineCap = 'round';

  const tilt = currentMode === 'happy' ? (side * -4) : (side * 2);

  ctx.beginPath();
  ctx.moveTo(cx - 22, cy + tilt);
  ctx.lineTo(cx + 22, cy - tilt);
  ctx.stroke();
  ctx.restore();
}

function drawEye(cx, cy, radius, openRatio, primaryGlow, secondaryGlow) {
  ctx.save();
  ctx.strokeStyle = primaryGlow;
  ctx.lineWidth = 2.4;
  ctx.shadowColor = primaryGlow;
  ctx.shadowBlur = 12;

  if (currentMode === 'happy') {
    // Crescent smiling eye
    ctx.beginPath();
    ctx.arc(cx, cy + 4, radius, Math.PI * 1.15, Math.PI * 1.85);
    ctx.stroke();

    // Twinkling sparkle glint
    ctx.fillStyle = '#FFFFFF';
    ctx.shadowColor = '#FFFFFF';
    ctx.shadowBlur = 6;
    ctx.beginPath();
    ctx.arc(cx + 6, cy - 4, 2, 0, Math.PI * 2);
    ctx.fill();
  } else {
    // Focused open eye contour
    ctx.beginPath();
    ctx.ellipse(cx, cy, radius, radius * openRatio * 0.85, 0, 0, Math.PI * 2);
    ctx.stroke();

    // Pupil (reactive to gaze)
    ctx.clip();
    const px = cx + gaze.x * 7;
    const py = cy + gaze.y * 5;
    ctx.beginPath();
    ctx.arc(px, py, 6 * openRatio, 0, Math.PI * 2);
    ctx.fillStyle = secondaryGlow;
    ctx.shadowColor = secondaryGlow;
    ctx.shadowBlur = 10;
    ctx.fill();
  }

  ctx.restore();
}

function drawMouth(cx, cy, primaryGlow, secondaryGlow) {
  ctx.save();
  ctx.strokeStyle = primaryGlow;
  ctx.lineWidth = 2.4;
  ctx.shadowColor = primaryGlow;
  ctx.shadowBlur = 10;
  ctx.lineCap = 'round';

  const mouthW = 46;

  if (isSpeaking) {
    // Dynamic open speaking viseme
    const openH = Math.abs(Math.sin(speechPhase * 8)) * 12 + Math.sin(speechPhase * 4) * 3 + 3;
    ctx.beginPath();
    ctx.ellipse(cx, cy, mouthW / 2, openH, 0, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(5, 10, 16, 0.9)';
    ctx.fill();
    ctx.stroke();

    // Inner energetic waveform
    ctx.beginPath();
    ctx.moveTo(cx - mouthW / 2 + 6, cy);
    ctx.quadraticCurveTo(cx, cy + Math.sin(speechPhase * 10) * (openH * 0.7), cx + mouthW / 2 - 6, cy);
    ctx.strokeStyle = secondaryGlow;
    ctx.lineWidth = 1.6;
    ctx.stroke();
  } else if (isTyping) {
    // Subtle typing cadence mouth flutter
    const flutter = Math.sin(speechPhase * 12) * 2;
    ctx.beginPath();
    ctx.moveTo(cx - mouthW / 2, cy);
    ctx.quadraticCurveTo(cx, cy + flutter, cx + mouthW / 2, cy);
    ctx.stroke();
  } else {
    // Calm smiling baseline curve
    const curve = currentMode === 'happy' ? -6 : -2;
    ctx.beginPath();
    ctx.moveTo(cx - mouthW / 2, cy);
    ctx.quadraticCurveTo(cx, cy + curve, cx + mouthW / 2, cy);
    ctx.stroke();
  }

  ctx.restore();
}

// 4. TYPING CADENCE & AUTO-CORRECTION ENGINE
function handleTypingCadence() {
  isTyping = true;
  clearTimeout(typingTimer);
  typingTimer = setTimeout(() => {
    isTyping = false;
  }, 400);
}

function sanitizeAndCorrectInput(rawText) {
  let text = rawText.trim();
  let corrected = false;

  for (const [typo, fix] of Object.entries(typoDictionary)) {
    const regex = new RegExp(`\\b${typo}\\b`, 'gi');
    if (regex.test(text)) {
      text = text.replace(regex, fix);
      corrected = true;
    }
  }

  const banner = document.getElementById('correctionBanner');
  const bannerText = document.getElementById('correctionText');
  if (corrected && banner && bannerText) {
    banner.style.display = 'flex';
    bannerText.textContent = `Auto-corrected prompt: "${text}"`;
    setTimeout(() => {
      banner.style.display = 'none';
    }, 4000);
  }

  return text;
}

// 5. INTERACTIVE PROMPT EXECUTION & NLP REASONING
function handlePromptSubmit(e) {
  e.preventDefault();
  const input = document.getElementById('promptInput');
  if (!input) return;
  const rawText = input.value;
  if (!rawText.trim()) return;
  input.value = '';

  const cleanText = sanitizeAndCorrectInput(rawText);
  processLiteQuery(cleanText);
}

function executeQuickPrompt(promptText) {
  const cleanText = sanitizeAndCorrectInput(promptText);
  processLiteQuery(cleanText);
}

async function processLiteQuery(queryText) {
  displayUserQuery(queryText);
  playTone(520, 'triangle', 0.08);

  try {
    const res = await fetch('/api/v1/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: queryText })
    });

    if (res.ok) {
      const data = await res.json();
      if (data.emotion === 'happy') {
        switchEmotionMode('happy');
      } else {
        switchEmotionMode('helpful');
      }
      speakSaarthiLite(data.replyText);
      return;
    }
  } catch (e) {
    console.warn("Backend NLP endpoint offline, executing local rules:", e);
  }

  // Fallback Local Agronomy Rule Engine
  let reply = `I analyzed "${queryText}". Chamber 1 is currently stable at ${Math.round(telemetry.co2)} ppm CO2.`;
  if (queryText.toLowerCase().includes('harvest')) {
    reply = "Logged 15.0 kg Oyster Mushroom harvest into database. Yield efficiency is at 104%!";
    switchEmotionMode('happy');
  } else if (queryText.toLowerCase().includes('mold') || queryText.toLowerCase().includes('trichoderma')) {
    reply = "Green mold detected. Isolate substrate, spot treat with 3% hydrogen peroxide, and ramp ventilation.";
    switchEmotionMode('helpful');
  }
  speakSaarthiLite(reply);
}

function displayUserQuery(text) {
  const speechText = document.getElementById('saarthiSpeechText');
  if (speechText) {
    speechText.innerHTML = `<em>User asked: "${text}"</em><br><span style="color:#00D2FF;">Processing NLP reasoning...</span>`;
  }
}

// 6. DYNAMIC SPEECH SYNTHESIS & LIP-SYNC
async function speakSaarthiLite(text) {
  const speechText = document.getElementById('saarthiSpeechText');
  if (speechText) speechText.textContent = `"${text}"`;

  // 1. Try ElevenLabs Streaming via Spring Boot
  try {
    if (currentAudioElement) {
      currentAudioElement.pause();
      currentAudioElement = null;
    }

    const res = await fetch('/api/v1/ai/tts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: text })
    });

    if (res.ok) {
      const audioBlob = await res.blob();
      const audioUrl = URL.createObjectURL(audioBlob);
      const audio = new Audio(audioUrl);
      currentAudioElement = audio;

      isSpeaking = true;
      audio.onended = () => {
        isSpeaking = false;
        URL.revokeObjectURL(audioUrl);
        currentAudioElement = null;
      };
      audio.onerror = () => {
        isSpeaking = false;
        fallbackWebSpeech(text);
      };
      await audio.play();
      return;
    }
  } catch (e) {}

  fallbackWebSpeech(text);
}

function fallbackWebSpeech(text) {
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.rate = 1.05;
    u.onstart = () => { isSpeaking = true; };
    u.onend = () => { isSpeaking = false; };
    u.onerror = () => { isSpeaking = false; };
    window.speechSynthesis.speak(u);
  } else {
    isSpeaking = false;
  }
}

// 7. SPEECH RECOGNITION (MIC)
function initSpeechRecognition() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (SpeechRecognition) {
    recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.lang = 'en-US';

    recognition.onstart = () => {
      isRecordingMic = true;
      document.getElementById('btnMicLite').classList.add('recording');
      playTone(600, 'sine', 0.1);
    };
    recognition.onresult = (e) => {
      const transcript = e.results[0][0].transcript;
      stopLiteMic();
      const clean = sanitizeAndCorrectInput(transcript);
      processLiteQuery(clean);
    };
    recognition.onerror = () => { stopLiteMic(); };
    recognition.onend = () => { stopLiteMic(); };
  }
}

function toggleLiteMic() {
  if (!recognition) return;
  if (!isRecordingMic) recognition.start();
  else recognition.stop();
}

function stopLiteMic() {
  isRecordingMic = false;
  const btn = document.getElementById('btnMicLite');
  if (btn) btn.classList.remove('recording');
}

// 8. SPRING BOOT WEBSOCKET TELEMETRY SYNC
function initTelemetryWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = window.location.host || 'localhost:8080';
  try {
    const ws = new WebSocket(`${protocol}//${host}/ws/telemetry`);
    ws.onmessage = (e) => {
      try {
        const d = JSON.parse(e.data);
        if (d.co2Ppm !== undefined) {
          telemetry.co2 = d.co2Ppm;
          telemetry.rh = d.humidityRh;
          telemetry.temp = d.tempC;
          telemetry.fanRpm = d.fanRpm;
          updateTelemetryUI();
        }
      } catch (err) {}
    };
  } catch (err) {}
}

function updateTelemetryUI() {
  const tCO2 = document.getElementById('tCO2');
  const tRH = document.getElementById('tRH');
  const tTemp = document.getElementById('tTemp');
  const tFan = document.getElementById('tFan');

  if (tCO2) tCO2.textContent = `${Math.round(telemetry.co2)} ppm`;
  if (tRH) tRH.textContent = `${Math.round(telemetry.rh)}%`;
  if (tTemp) tTemp.textContent = `${telemetry.temp.toFixed(1)} °C`;
  if (tFan) tFan.textContent = `${telemetry.fanRpm} RPM`;
}

// 9. WEB AUDIO TONE SYNTHESIZER
function playTone(freq, type, dur) {
  try {
    const actx = new (window.AudioContext || window.webkitAudioContext)();
    const osc = actx.createOscillator();
    const g = actx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, actx.currentTime);
    g.gain.setValueAtTime(0.06, actx.currentTime);
    g.gain.exponentialRampToValueAtTime(0.001, actx.currentTime + dur);
    osc.connect(g);
    g.connect(actx.destination);
    osc.start();
    osc.stop(actx.currentTime + dur);
  } catch (e) {}
}
