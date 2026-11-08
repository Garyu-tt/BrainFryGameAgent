/**
 * OPTIONAL Minecraft client demo (Node 20+).
 * In a separate directory: npm install mineflayer
 * FLY_MC_HOST=127.0.0.1 FLY_MC_PORT=25565 node examples/minecraft_mineflayer.mjs
 * Use your own offline/test server. Does not import BrainFly private world/state.
 */
import mineflayer from 'mineflayer';

const host = process.env.FLY_MC_HOST || '127.0.0.1';
const port = Number(process.env.FLY_MC_PORT || 25565);
const endpoint = 'http://127.0.0.1:8765/v1/step';
const bot = mineflayer.createBot({
  host, port, username: process.env.FLY_MC_USER || 'FlyGameDemo',
  auth: process.env.FLY_MC_AUTH || 'offline',
});
let pending = false;
let last = null;
let controlTimeout = null;

function stopMotion() {
  for (const key of ['forward', 'back', 'left', 'right', 'jump', 'sprint']) {
    bot.setControlState(key, false);
  }
  if (controlTimeout) clearTimeout(controlTimeout);
  controlTimeout = null;
}

function observation() {
  const pos = bot.entity?.position;
  let nearestThreat = Infinity;
  for (const entity of Object.values(bot.entities || {})) {
    if (!entity.position || !pos || entity === bot.entity) continue;
    if (!['zombie','skeleton','creeper','spider','drowned'].includes(entity.name)) continue;
    nearestThreat = Math.min(nearestThreat, entity.position.distanceTo(pos));
  }
  const threat = Number.isFinite(nearestThreat) ? Math.max(0, 1 - nearestThreat / 12) : 0;
  const moved = pos && last ? pos.distanceTo(last) : 0;
  last = pos?.clone();
  return {
    hunger: Math.max(0, Math.min(1, (20 - (bot.food ?? 20)) / 20)),
    threat, novelty: .4, fatigue: 0,
    obstacle: moved < .05 && bot.getControlState('forward') ? .7 : 0,
    food: 0, // A real food detector must be implemented by the game adapter.
    reward: moved > .1 ? .3 : 0,
  };
}

async function drive() {
  if (pending || !bot.entity) return;
  pending = true;
  try {
    const response = await fetch(endpoint, {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({observation: observation()}),
      signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) throw new Error('fly API status ' + response.status);
    const result = await response.json();
    stopMotion();
    if (result.action === 'forward') bot.setControlState('forward', true);
    if (result.action === 'left') bot.setControlState('left', true);
    if (result.action === 'right') bot.setControlState('right', true);
    if (result.action === 'retreat') bot.setControlState('back', true);
    // INTERACT deliberately does not break/place blocks in this safe starter demo.
    controlTimeout = setTimeout(stopMotion, 170);
    process.stdout.write(result.action + ' (' + result.spikes + ' spikes)\n');
  } catch (error) {
    stopMotion();
    console.error('fly disconnected:', error.message);
  } finally {
    pending = false;
  }
}

let timer;
bot.once('spawn', () => {
  console.log('Connected to test Minecraft server:', host, port);
  timer = setInterval(drive, 350);
});
bot.on('end', () => { clearInterval(timer); stopMotion(); });
bot.on('error', (error) => console.error('Minecraft:', error.message));
process.on('SIGINT', () => { clearInterval(timer); stopMotion(); bot.quit(); process.exit(0); });
