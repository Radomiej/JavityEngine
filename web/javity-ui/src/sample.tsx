import { JavityReact, React, useAction, useLayoutEffect, useSignal, useState } from './runtime.js';

const { View, Text, Button, TextField, Progress, Toggle, Slider } = JavityReact;

export function Counter({ onIncrement }: { onIncrement?: () => void } = {}) {
  const [count, setCount] = useState(0);
  useLayoutEffect(() => {
    globalThis.counterReady = true;
    return () => { globalThis.counterReady = false; };
  }, []);
  return <View id="screen" key="screen" className="flex-col gap-2 p-3 w-[320px]">
    <Text id="label" key="label" className="text-lg text-white">Count: {count}</Text>
    <Button id="increment" key="increment" className="bg-blue-600 text-white" text="Add" onClick={() => { onIncrement?.(); setCount(value => value + 1); }} />
  </View>;
}

// This entry is bundled for native embedding; the Java host attaches before calling mountSample().
globalThis.mountSample = (onIncrement) => JavityReact.render(<Counter onIncrement={onIncrement} />);

function ControlledControls() {
  const [name, setName] = useState('start');
  return <View id="controlled-panel" className="flex-col gap-2">
    <TextField id="react-controlled-field" className="w-[260px] h-[36px] bg-white text-black" value={name}
      onChange={(value) => { globalThis.controlledChangeCalls++; setName(value); }} />
    <Progress id="react-controlled-progress" className="w-[260px] h-[18px]" value={Math.min(1, name.length / 10)} />
  </View>;
}
function PluginsGallery({ onIncrement }: { onIncrement?: () => void }) {
  return <React.Fragment><Counter onIncrement={onIncrement} /><ControlledControls /></React.Fragment>;
}
globalThis.mountPluginsGallery = (onIncrement) => {
  globalThis.controlledChangeCalls = 0;
  JavityReact.render(<PluginsGallery onIncrement={onIncrement} />);
};

function SettingsControls() {
  const [volume, setVolume] = useState(0.5);
  const [muted, setMuted] = useState(false);
  const setVolumeAction = useAction('setVolume');
  const setMutedAction = useAction('setMuted');
  const [keyEvent, setKeyEvent] = useState('');
  return <View id="settings-controls" className="flex-col gap-2 p-3 w-[260px] bg-slate-800 rounded-lg">
    <Text id="settings-title" className="text-base text-white">Audio settings</Text>
    <Toggle id="settings-muted" className="w-[52px] h-[32px] border border-slate-700 focus:border-cyan-400 disabled:opacity-50"
      checked={muted} onChange={(next) => { setMuted(next); setMutedAction(next); }}
      onKeyDown={(event) => { globalThis.lastControlKey = event; setKeyEvent(`${event.key} shift=${event.shift}`); return false; }}
      onKeyUp={(event) => { globalThis.lastControlKeyUp = event; return false; }}
      onFocus={() => { globalThis.controlFocus = (globalThis.controlFocus || 0) + 1; }}
      onBlur={() => { globalThis.controlBlur = (globalThis.controlBlur || 0) + 1; }} />
    <Text id="settings-volume-label" className="text-sm text-white">Volume: {volume.toFixed(1)}</Text>
    <Slider id="settings-volume" className="w-[220px] h-[32px] border border-slate-700 focus:border-cyan-400 disabled:opacity-50"
      min={0} max={1} value={volume} step={0.1}
      onChange={(next) => { setVolume(next); setVolumeAction(next); }}
      onKeyDown={(event) => { globalThis.lastControlKey = event; setKeyEvent(`${event.key} shift=${event.shift}`); return event.key === 'ArrowLeft'; }}
      onFocus={() => { globalThis.controlFocus = (globalThis.controlFocus || 0) + 1; }}
      onBlur={() => { globalThis.controlBlur = (globalThis.controlBlur || 0) + 1; }} />
    <Toggle id="settings-disabled" className="w-[52px] h-[32px] disabled:bg-slate-700" checked={false} disabled />
    <Text id="settings-key-event" className="text-sm text-white">{keyEvent}</Text>
  </View>;
}
globalThis.mountSettingsControls = () => JavityReact.render(<SettingsControls />);

export function HeadlessCounter() {
  const [count, setCount] = useState(1);
  useLayoutEffect(() => {
    globalThis.headlessEffectMounted = true;
    return () => { globalThis.headlessEffectMounted = false; };
  }, []);
  globalThis.bumpHeadlessCounter = () => setCount(value => value + 1);
  return <div id="headless-screen" style={{ width: count * 10 }} />;
}
globalThis.mountHeadlessCounter = () => JavityReact.render(<HeadlessCounter />);

export function NestedViewFixture({ reverse = false, wide = false }: { reverse?: boolean; wide?: boolean }) {
  const items = reverse ? ['right', 'left'] : ['left', 'right'];
  return <div id="nested-root" className={wide ? 'flex-col gap-3 w-[240px]' : 'flex-col gap-1 w-[120px]'}>
    <div id="nested-row" className="flex-row gap-2">
      {items.map((name) => <div key={name} id={`nested-${name}`} className="flex-col gap-1 w-[40px]">
        <div id={`nested-${name}-inner`} className="flex-row w-[20px]" />
      </div>)}
    </div>
  </div>;
}
globalThis.mountNestedViewFixture = (reverse = false, wide = false) =>
  JavityReact.render(<NestedViewFixture reverse={reverse} wide={wide} />);

export function RadioValueFixture({ name = 'score' }: { name?: string }) {
  const value = useSignal(name);
  return <div id="radio-fixture" className={`flex-col gap-[${value}px]`} />;
}
globalThis.mountRadioValueFixture = (name = 'score') => JavityReact.render(<RadioValueFixture name={name} />);
globalThis.mountRadioAndNestedFixture = () =>
  JavityReact.render(<React.Fragment><RadioValueFixture /><NestedViewFixture /></React.Fragment>);
