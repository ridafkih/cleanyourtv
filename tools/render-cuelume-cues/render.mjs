import { OfflineAudioContext } from 'node-web-audio-api';
import { writeFileSync } from 'node:fs';

const SAMPLE_RATE = 44100;
const SECONDS = 1;
const TARGET_PEAK = 0.5;
let current;
class RenderContext extends OfflineAudioContext {
  constructor() {
    super(2, SAMPLE_RATE * SECONDS, SAMPLE_RATE);
    current = this;
  }
  get state() {
    return 'running';
  }
}
globalThis.window = globalThis;
globalThis.AudioContext = RenderContext;

const engine = new URL('./node_modules/cuelume/dist/audio/engine.js', import.meta.url).href;

function wav(buffer, samples, gain) {
  const channels = buffer.numberOfChannels;
  const data = Buffer.alloc(samples * channels * 2);
  for (let i = 0; i < samples; i++) {
    for (let c = 0; c < channels; c++) {
      const v = Math.max(-1, Math.min(1, buffer.getChannelData(c)[i] * gain));
      data.writeInt16LE(Math.round(v * 32767), (i * channels + c) * 2);
    }
  }
  const header = Buffer.alloc(44);
  header.write('RIFF', 0);
  header.writeUInt32LE(36 + data.length, 4);
  header.write('WAVEfmt ', 8);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(channels, 22);
  header.writeUInt32LE(SAMPLE_RATE, 24);
  header.writeUInt32LE(SAMPLE_RATE * channels * 2, 28);
  header.writeUInt16LE(channels * 2, 32);
  header.writeUInt16LE(16, 34);
  header.write('data', 36);
  header.writeUInt32LE(data.length, 40);
  return Buffer.concat([header, data]);
}

for (const cue of process.argv.slice(2)) {
  const { play } = await import(`${engine}?cue=${cue}`);
  current = undefined;
  play(cue);
  if (!current) throw new Error(`no context for ${cue}`);
  const buffer = await current.startRendering();
  let peak = 0;
  let last = 0;
  const left = buffer.getChannelData(0);
  for (let i = 0; i < left.length; i++) {
    const a = Math.abs(left[i]);
    peak = Math.max(peak, a);
    if (a > 0.002) last = i;
  }
  const samples = Math.min(left.length, last + Math.round(SAMPLE_RATE * 0.02));
  writeFileSync(new URL(`./cue_${cue}.wav`, import.meta.url), wav(buffer, samples, TARGET_PEAK / peak));
  console.log(cue, 'peak', peak.toFixed(3), 'ms', Math.round(samples / SAMPLE_RATE * 1000));
}
