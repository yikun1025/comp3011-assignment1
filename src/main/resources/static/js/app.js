"use strict";

const startButton = document.getElementById("start-button");
const stopButton = document.getElementById("stop-button");
const indicator = document.getElementById("recording-indicator");
const timerLabel = document.getElementById("timer");
const statusLabel = document.getElementById("status");
const transcriptBox = document.getElementById("transcript");
const errorBox = document.getElementById("error");
const errorMessage = document.getElementById("error-message");

const IDLE = "idle";
const RECORDING = "recording";
const TRANSCRIBING = "transcribing";

let state = IDLE;
let tickHandle = null;
let startedAt = 0;

function setState(next) {
    state = next;

    startButton.disabled = next !== IDLE;
    stopButton.disabled = next !== RECORDING;
    indicator.hidden = next !== RECORDING;

    if (next === IDLE) {
        statusLabel.textContent = "Ready";
        stopTimer();
    } else if (next === RECORDING) {
        statusLabel.textContent = "Recording";
        startTimer();
    } else if (next === TRANSCRIBING) {
        statusLabel.textContent = "Transcribing";
        stopTimer();
    }
}

function startTimer() {
    startedAt = Date.now();
    timerLabel.textContent = "00:00";
    tickHandle = setInterval(() => {
        const elapsed = Math.floor((Date.now() - startedAt) / 1000);
        const minutes = String(Math.floor(elapsed / 60)).padStart(2, "0");
        const seconds = String(elapsed % 60).padStart(2, "0");
        timerLabel.textContent = `${minutes}:${seconds}`;
    }, 200);
}

function stopTimer() {
    if (tickHandle !== null) {
        clearInterval(tickHandle);
        tickHandle = null;
    }
}

function showError(message) {
    errorMessage.textContent = message;
    errorBox.hidden = false;
}

function clearError() {
    errorBox.hidden = true;
    errorMessage.textContent = "";
}

/*
 * Container negotiation.
 *
 * Chrome and Firefox produce WebM/Opus; Safari does not support it and
 * produces MP4/AAC. Hard-coding one type makes the page silently fail on
 * the other family, so the list is probed in preference order and the
 * winner is reported to the server, which needs it to name the upload.
 */
const PREFERRED_TYPES = [
    "audio/webm;codecs=opus",
    "audio/webm",
    "audio/mp4",
    "audio/ogg;codecs=opus",
];

/*
 * Compression.
 *
 * Speech is intelligible well below music bitrates, and the upload sits
 * inside a five-second budget that includes a transatlantic round trip.
 * 24 kbps mono is roughly 3 KB per second of audio, so a thirty-second
 * clip travels as about 90 KB.
 */
const RECORDER_OPTIONS = {
    audioBitsPerSecond: 24000,
};

const AUDIO_CONSTRAINTS = {
    audio: {
        channelCount: 1,
        sampleRate: 16000,
        echoCancellation: true,
        noiseSuppression: true,
    },
};

let recorder = null;
let chunks = [];
let stream = null;

function pickMimeType() {
    for (const type of PREFERRED_TYPES) {
        if (MediaRecorder.isTypeSupported(type)) {
            return type;
        }
    }
    return "";
}

async function startRecording() {
    clearError();

    if (!navigator.mediaDevices || !window.MediaRecorder) {
        showError("This browser cannot record audio. Recording requires a secure page (https or localhost).");
        return;
    }

    try {
        stream = await navigator.mediaDevices.getUserMedia(AUDIO_CONSTRAINTS);
    } catch (err) {
        showError("Microphone access was refused. Allow it in the browser's site settings and try again.");
        return;
    }

    const mimeType = pickMimeType();
    const options = mimeType ? { ...RECORDER_OPTIONS, mimeType } : RECORDER_OPTIONS;

    chunks = [];
    recorder = new MediaRecorder(stream, options);

    recorder.addEventListener("dataavailable", (event) => {
        if (event.data && event.data.size > 0) {
            chunks.push(event.data);
        }
    });

    recorder.addEventListener("stop", () => {
        releaseMicrophone();
        const blob = new Blob(chunks, { type: recorder.mimeType });
        chunks = [];
        uploadForTranscription(blob);
    });

    recorder.start();
    setState(RECORDING);
}

/*
 * The client gives up later than the server does.
 *
 * The server's read timeout against the STT provider is ten seconds, and
 * a request that exceeds it comes back as a 504 the user can be shown.
 * Aborting first would replace that explanation with an unexplained
 * silence, so the client window sits above it.
 */
const CLIENT_TIMEOUT_MS = 15000;

async function uploadForTranscription(blob) {
    const body = new FormData();
    body.append("audio", blob, "recording");

    const controller = new AbortController();
    const timeoutHandle = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);

    try {
        const response = await fetch("/api/v1/transcribe", {
            method: "POST",
            body: body,
            signal: controller.signal,
        });

        if (!response.ok) {
            showError(await describeFailure(response));
            return;
        }

        const payload = await response.json();
        showTranscript(payload.text);
    } catch (err) {
        if (err.name === "AbortError") {
            showError("Transcription took too long and was cancelled. Try a shorter recording.");
        } else {
            showError("Could not reach the server. Check your connection and try again.");
        }
    } finally {
        clearTimeout(timeoutHandle);
        setState(IDLE);
    }
}

/*
 * The server answers every failure with the same JSON shape, so its
 * message is preferred. A proxy or a crash can still produce something
 * else, hence the fallback.
 */
async function describeFailure(response) {
    try {
        const problem = await response.json();
        if (problem && problem.message) {
            return problem.message;
        }
    } catch (err) {
        // Body was not the expected JSON; fall through.
    }
    return `Transcription failed (HTTP ${response.status}).`;
}

function showTranscript(text) {
    transcriptBox.textContent = "";
    const paragraph = document.createElement("p");
    paragraph.textContent = text;
    transcriptBox.appendChild(paragraph);
}

function stopRecording() {
    if (recorder && recorder.state === "recording") {
        setState(TRANSCRIBING);
        recorder.stop();
    }
}

/*
 * Releasing the tracks is what turns the browser's recording indicator
 * off. Stopping the recorder alone leaves the microphone open.
 */
function releaseMicrophone() {
    if (stream) {
        stream.getTracks().forEach((track) => track.stop());
        stream = null;
    }
}

startButton.addEventListener("click", startRecording);
stopButton.addEventListener("click", stopRecording);

setState(IDLE);