import { JavityReact, React, useAction, useSignal } from './runtime.js';

const { View, Text, Button, Toggle, Slider, Progress } = JavityReact;

function DevelopmentHudRoot() {
  const generation = useSignal('sceneGeneration');
  return <DevelopmentHud key={generation} generation={generation} />;
}

function DevelopmentHud({ generation }) {
  const health = useSignal('health');
  const volumeValue = useSignal('volume');
  const muted = useSignal('muted');
  const loadProgress = useSignal('loadProgress');
  const loadStatus = useSignal('loadStatus');
  const fire = useAction('fire');
  const heal = useAction('heal');
  const deploy = useAction('deploy');
  const cancel = useAction('cancel');
  const setVolume = useAction('volume');
  const setMuted = useAction('mute');

  return (
    <View id="dev-hud" className="flex-col gap-2 p-3 w-[300px] bg-slate-800 rounded-lg">
      <Text id="dev-health" className="text-lg text-white">Health: {health}</Text>
      <Text id="dev-generation" className="text-sm text-white">Scene: {generation}</Text>
      <View id="dev-actions" className="flex-row gap-2">
        <Button id="dev-fire" className="bg-blue-600 text-white" text="Fire" onClick={fire} />
        <Button id="dev-heal" className="bg-emerald-500 text-slate-900" text="Heal" onClick={heal} />
        <Button id="dev-deploy" className="bg-cyan-400 text-slate-900" text="Deploy" onClick={deploy} />
        <Button id="dev-cancel" className="bg-red-500 text-slate-900" text="Cancel" onClick={cancel} />
      </View>
      <Text id="dev-volume-label" className="text-sm text-white">Volume: {Number(volumeValue).toFixed(2)}</Text>
      <Slider
        id="dev-volume"
        className="w-[260px] h-[28px]"
        min={0}
        max={1}
        step={0.05}
        value={volumeValue}
        onChange={setVolume}
      />
      <Toggle id="dev-muted" className="w-[52px] h-[30px]" checked={muted} onChange={setMuted} />
      <Text id="dev-load-status" className="text-sm text-white">{loadStatus}</Text>
      <Progress id="dev-load-progress" className="w-[260px] h-[18px]" value={loadProgress} />
    </View>
  );
}

JavityReact.render(React.createElement(DevelopmentHudRoot));
