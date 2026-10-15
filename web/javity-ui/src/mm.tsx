import { JavityReact, React, useAction, useSignal } from './runtime.js';
import fixture from '../../javity-ui-reference/mm-fixture.json';

const { View, Text, Button, Image, Progress, Toggle, Slider } = JavityReact;
const COMPONENTS = { View, Text, Button, Image, Progress, Toggle, Slider };
const column = fixture.tree.children[0];
const [sceneTree, settingsTree] = column.children;

function nativeNode(node, values, actions) {
  const Component = COMPONENTS[node.type];
  const props = { id: node.key, className: node.classes };
  let children = (node.children || []).map(child => nativeNode(child, values, actions));
  if (node.type === 'Text') children = node.bind ? values[node.bind] : node.text;
  if (node.type === 'Image') Object.assign(props, { source: 'pilot', fit: node.props.fit });
  if (node.type === 'Button') Object.assign(props, {
    text: node.props.text,
    disabled: node.props.disabledSignal ? !values[node.props.disabledSignal] : false,
    onClick: actions[node.props.action]
  });
  if (node.type === 'Progress') Object.assign(props, {
    value: values[node.props.valueSignal], min: node.props.min, max: node.props.max,
    duration: node.props.duration, delay: node.props.delay
  });
  if (node.type === 'Toggle') Object.assign(props, {
    checked: values[node.props.checkedSignal], onChange: actions[node.props.action]
  });
  if (node.type === 'Slider') Object.assign(props, {
    min: node.props.min, max: node.props.max, step: node.props.step,
    value: values[node.props.valueSignal], onChange: actions[node.props.action]
  });
  return React.createElement(Component, props, ...(Array.isArray(children) ? children : [children]));
}

function SceneHud() {
  const health = useSignal('health');
  const generation = useSignal('sceneGeneration');
  const droneState = useSignal('droneState');
  const canFire = useSignal('canFire');
  const loadProgress = useSignal('loadProgress');
  const loadStatus = useSignal('loadStatus');
  const fire = useAction('fire');
  const heal = useAction('heal');
  const deploy = useAction('deploy');
  const cancel = useAction('cancel');
  const loading = loadStatus === 'LOADING' || loadStatus === 'FADING_OUT' || loadStatus === 'FADING_IN';
  const canCancel = loadStatus === 'LOADING' || loadStatus === 'FADING_OUT';
  const values = {
    health, healthPercent: `${Math.round(health * 100)}%`, healthNumber: `${Math.round(health * 100)} / 100`,
    generation: `GEN ${Math.floor(generation)}`, droneState, canFire, loadProgress, loadStatus,
    canDeploy: !loading, canCancel
  };
  return nativeNode(sceneTree, values, { fire, heal, deploy, cancel });
}

function SettingsPanel() {
  const volume = useSignal('volume');
  const muted = useSignal('muted');
  const setVolume = useAction('volume');
  const setMuted = useAction('mute');
  const values = { volume, volumePercent: `${Math.round(volume * 100)}%`, muted };
  return nativeNode(settingsTree, values, { volume: setVolume, mute: setMuted });
}

function MmHudRoot() {
  const generation = useSignal('sceneGeneration');
  return React.createElement(View, { id: fixture.tree.key, className: fixture.tree.classes },
    React.createElement(View, { id: column.key, className: column.classes },
      React.createElement(SceneHud, { key: `scene-${Math.floor(generation)}` }),
      React.createElement(SettingsPanel)));
}

JavityReact.render(React.createElement(MmHudRoot));
