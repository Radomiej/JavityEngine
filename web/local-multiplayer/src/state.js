import { schema, t } from '@colyseus/schema';

export const PlayerState = schema({
  x: t.number(),
  z: t.number(),
  lastSequence: t.number(),
}, 'PlayerState');

export const RoomState = schema({
  tick: t.number(),
  players: t.map(PlayerState),
}, 'RoomState');
