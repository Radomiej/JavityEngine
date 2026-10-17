const { Client } = window.Colyseus;
const status = document.querySelector('#status');
const roomIdField = document.querySelector('#room-id');
const roomLabel = document.querySelector('#current-room');
const tickLabel = document.querySelector('#tick');
const playerList = document.querySelector('#players');
const arena = document.querySelector('#arena');
const createButton = document.querySelector('#create');
const joinButton = document.querySelector('#join');
const leaveButton = document.querySelector('#leave');
const client = new Client(location.origin);
let room;
let connecting = false;
let sequence = 0;
let currentInput = { x: 0, z: 0 };
const held = new Set();

async function connect(action, roomId) {
  if (room || connecting) return;
  connecting = true;
  createButton.disabled = joinButton.disabled = true;
  status.textContent = `Connecting (${action})…`;
  try {
    room = action === 'create'
      ? await client.create('javity-local')
      : await client.joinById(roomId.trim());
    room.reconnection.enabled = false;
    roomIdField.value = room.roomId;
    roomLabel.textContent = room.roomId;
    createButton.disabled = joinButton.disabled = true;
    leaveButton.disabled = false;
    connecting = false;
    status.textContent = `Joined as ${room.sessionId}`;
    room.onStateChange((state) => render(state));
    room.onLeave((code) => {
      status.textContent = `Left room (${code})`;
      clearRoom();
    });
    render(room.state);
  } catch (error) {
    const acquired = room;
    room = undefined;
    if (acquired) await acquired.leave().catch(() => {});
    clearRoom();
    status.textContent = `Could not ${action}: ${error.message}`;
    connecting = false;
    createButton.disabled = joinButton.disabled = false;
  }
}

function clearRoom() {
  room = undefined;
  held.clear();
  currentInput = { x: 0, z: 0 };
  createButton.disabled = joinButton.disabled = false;
  leaveButton.disabled = true;
  roomLabel.textContent = '—';
  tickLabel.textContent = '—';
  playerList.replaceChildren();
  arena.replaceChildren();
}

function render(state) {
  if (!state || !state.players || typeof state.players[Symbol.iterator] !== 'function') {
    tickLabel.textContent = state?.tick ?? '—';
    playerList.replaceChildren();
    arena.replaceChildren();
    return;
  }
  tickLabel.textContent = state.tick;
  const rows = [];
  const dots = [];
  for (const [sessionId, player] of state.players) {
    const row = document.createElement('li');
    row.textContent = `${sessionId}: x ${Number(player.x).toFixed(2)}, z ${Number(player.z).toFixed(2)}, seq ${player.lastSequence}`;
    rows.push(row);
    const dot = document.createElement('span');
    dot.className = `player-dot${sessionId === room?.sessionId ? ' self' : ''}`;
    dot.title = sessionId;
    dot.setAttribute('aria-label', row.textContent);
    dot.style.left = `${((player.x + 50) / 100) * 100}%`;
    dot.style.top = `${((player.z + 50) / 100) * 100}%`;
    dots.push(dot);
  }
  playerList.replaceChildren(...rows);
  arena.replaceChildren(...dots);
}

function updateIntent() {
  const x = Number(held.has('d') || held.has('arrowright')) - Number(held.has('a') || held.has('arrowleft'));
  const z = Number(held.has('s') || held.has('arrowdown')) - Number(held.has('w') || held.has('arrowup'));
  currentInput = { x, z };
}

const keyNames = new Map([
  ['w', 'w'], ['a', 'a'], ['s', 's'], ['d', 'd'],
  ['arrowup', 'arrowup'], ['arrowleft', 'arrowleft'], ['arrowdown', 'arrowdown'], ['arrowright', 'arrowright'],
]);
window.addEventListener('keydown', (event) => {
  const target = event.target;
  if (event.altKey || event.ctrlKey || event.metaKey
    || target instanceof HTMLElement && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) return;
  const key = keyNames.get(event.key.toLowerCase());
  if (!key || !room) return;
  event.preventDefault();
  held.add(key);
  updateIntent();
});
window.addEventListener('keyup', (event) => {
  const key = keyNames.get(event.key.toLowerCase());
  if (!key) return;
  held.delete(key);
  updateIntent();
});
window.addEventListener('blur', () => { held.clear(); updateIntent(); });
setInterval(() => {
  if (room) room.send('input', { sequence: sequence++, ...currentInput });
}, 100);

createButton.addEventListener('click', () => void connect('create'));
joinButton.addEventListener('click', () => void connect('join', roomIdField.value));
leaveButton.addEventListener('click', () => { void room?.leave(); });

fetch('/health').then((response) => {
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  status.textContent = 'Gateway ready. Create a room or enter a room ID.';
}).catch((error) => { status.textContent = `Gateway health check failed: ${error.message}`; });
