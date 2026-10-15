const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

function loadHost() {
  let committed;
  let handlers = {};
  const context = vm.createContext({
    console,
    setTimeout,
    clearTimeout,
    queueMicrotask,
    JavityNative: { commit: (tree, nextHandlers) => { committed = JSON.parse(tree); handlers = nextHandlers; }, isOpen: () => true },
    JavityScheduler: {
      now: () => 0,
      timeout: () => 1,
      clearTimeout: () => {},
      interval: () => 1,
      clearInterval: () => {},
      frame: () => 1,
      cancelFrame: () => {},
      microtask: (callback) => queueMicrotask(callback),
    },
  });
  const bundle = path.resolve(__dirname, '../../../javity-script/src/main/resources/org/javity/script/javity-ui.js');
  vm.runInContext(fs.readFileSync(bundle, 'utf8'), context);
  return { context, get committed() { return committed; }, get handlers() { return handlers; } };
}

function loadSample(context) {
  const bundle = path.resolve(__dirname, '../../../javity-script/src/main/resources/org/javity/script/javity-sample.js');
  vm.runInContext(fs.readFileSync(bundle, 'utf8'), context);
}

test('normalizes interleaved raw text at its original child positions', () => {
  const host = loadHost();
  vm.runInContext(`
    const { React, View, Text } = JavityReact;
    JavityReact.render(React.createElement(View, { id: 'parent' },
      'before',
      React.createElement(Text, { id: 'middle' }, 'middle'),
      'after'));
  `, host.context);
  assert.deepEqual(Array.from(host.committed.children, node => node.props.text ?? node.key), [
    'before', 'middle', 'after',
  ]);
  assert.equal(host.committed.children[1].key, 'middle');
});

test('preserves every native child of a React Fragment root', () => {
  const host = loadHost();
  vm.runInContext(`
    const { React, View } = JavityReact;
    JavityReact.render(React.createElement(React.Fragment, null,
      React.createElement(View, { id: 'fragment-a' }),
      React.createElement(View, { id: 'fragment-b' })));
  `, host.context);
  assert.equal(host.committed.type, 'View');
  assert.deepEqual(Array.from(host.committed.children, node => node.key), ['fragment-a', 'fragment-b']);
});

test('flushes React state updates synchronously after a native click handler', () => {
  const host = loadHost();
  vm.runInContext(`
    const { React, View, Text, Button } = JavityReact;
    function Counter() {
      const [count, setCount] = React.useState(0);
      return React.createElement(View, { id: 'click-screen' },
        React.createElement(Text, { id: 'click-count' }, String(count)),
        React.createElement(Button, { id: 'click-button', onClick: () => setCount(value => value + 1) }, 'Add'));
    }
    JavityReact.render(React.createElement(Counter));
  `, host.context);
  assert.equal(host.committed.children[0].props.text, '0');
  assert.equal(Object.keys(host.handlers).length, 1);
  const click = host.handlers[Object.keys(host.handlers)[0]];
  click();
  assert.equal(host.committed.children[0].props.text, '1');
  click();
  assert.equal(host.committed.children[0].props.text, '2');
});

test('sample click runs its optional game callback and updates its React state', () => {
  const host = loadHost();
  loadSample(host.context);
  vm.runInContext('globalThis.gameCalls=0; mountSample(()=>gameCalls++)', host.context);
  const handlerId = Object.keys(host.handlers)[0];
  assert.ok(handlerId);
  host.handlers[handlerId]();
  assert.equal(host.context.gameCalls, 1);
  assert.equal(host.committed.children.find(node => node.key === 'label').props.text, 'Count: 1');
});

test('useAction keeps a stable callback and returns undefined for void actions', () => {
  let cachedCallback;
  let cachedDependencies;
  const calls = [];
  const React = {
    useCallback(callback, dependencies) {
      if (cachedDependencies?.length === dependencies.length
          && dependencies.every((value, index) => value === cachedDependencies[index])) {
        return cachedCallback;
      }
      cachedDependencies = dependencies;
      cachedCallback = callback;
      return callback;
    },
  };
  for (const hook of ['useLayoutEffect', 'useEffect', 'useState', 'useRef', 'useMemo', 'useContext']) {
    React[hook] = () => {};
  }
  const context = vm.createContext({
    JavityReact: { React },
    JavityGame: { call: (...args) => { calls.push(args); return null; } },
  });
  const source = fs.readFileSync(path.resolve(__dirname, '../src/runtime.js'), 'utf8')
    .replace(/^export const /gm, 'const ')
    .replace(/^export function /gm, 'function ')
    + '\nglobalThis.testUseAction = useAction;';
  vm.runInContext(source, context);

  const first = context.testUseAction('heal');
  const second = context.testUseAction('heal');
  assert.equal(first, second);
  assert.equal(first(4, 'fast'), undefined);
  assert.deepEqual(calls, [['heal', 4, 'fast']]);
});

test('Progress accepts finite nonnegative duration and delay and rejects invalid timing props', () => {
  const host = loadHost();
  vm.runInContext(`
    {
    const { React, Progress } = JavityReact;
    JavityReact.render(React.createElement(Progress, { id: 'timed', value: .5, duration: .4, delay: .1 }));
    }
  `, host.context);
  assert.equal(host.committed.props.duration, .4);
  assert.equal(host.committed.props.delay, .1);

  const badProgressHost = loadHost();
  assert.throws(() => vm.runInContext(`
    {
    const { React, Progress } = JavityReact;
    JavityReact.render(React.createElement(Progress, { id: 'bad', duration: -1 }));
    }
  `, badProgressHost.context), /nonnegative finite Progress number/);
  const wrongNodeHost = loadHost();
  assert.throws(() => vm.runInContext(`
    {
    const { React, Button } = JavityReact;
    JavityReact.render(React.createElement(Button, { id: 'wrong', duration: 1 }));
    }
  `, wrongNodeHost.context), /nonnegative finite Progress number/);
});

test('Toggle and Slider emit controlled primitive props and all declared event handlers', () => {
  const host = loadHost();
  vm.runInContext(`
    const { React, View, Toggle, Slider } = JavityReact;
    JavityReact.render(React.createElement(View, null,
      React.createElement(Toggle, { id: 'mute', checked: false, onChange: () => {}, onFocus: () => {}, onKeyDown: () => false }),
      React.createElement(Slider, { id: 'volume', min: 0, max: 1, step: .1, value: .5, onChange: () => {}, onKeyUp: () => false, onBlur: () => {} })));
  `, host.context);
  assert.equal(host.committed.children[0].type, 'Toggle');
  assert.equal(host.committed.children[0].props.checked, false);
  assert.equal(host.committed.children[1].type, 'Slider');
  assert.equal(host.committed.children[1].props.value, .5);
  assert.equal(host.committed.children[1].props.step, .1);
  assert.equal(Object.keys(host.handlers).length, 6);
});

test('Image and img accept only a registered-source name and the three fit modes', () => {
  const host = loadHost();
  vm.runInContext(`
    const { React } = JavityReact;
    JavityReact.render(React.createElement('img', { id: 'avatar', source: 'k4-fixture', fit: 'cover', className: 'rounded-full' }));
  `, host.context);
  assert.equal(host.committed.type, 'Image');
  assert.equal(host.committed.props.source, 'k4-fixture');
  assert.equal(host.committed.props.fit, 'cover');
  for (const props of [
    { source: 4 },
    { source: 'k4-fixture', fit: 'contain cover' },
    { src: 'https://example.test/image.png' },
  ]) {
    const invalid = loadHost();
    assert.throws(() => vm.runInContext(`const { React, Image } = JavityReact; JavityReact.render(React.createElement(Image, ${JSON.stringify(props)}));`, invalid.context));
  }
});

test('Toggle and Slider reject wrong types, ranges, and unsupported control props', () => {
  const cases = [
    [`const { React, Toggle }=JavityReact; JavityReact.render(React.createElement(Toggle,{checked:1}))`, /checked must be a boolean/],
    [`const { React, Slider }=JavityReact; JavityReact.render(React.createElement(Slider,{value:'0.5'}))`, /Slider value must be a finite number/],
    [`const { React, Slider }=JavityReact; JavityReact.render(React.createElement(Slider,{step:0}))`, /step must be positive/],
    [`const { React, Slider }=JavityReact; JavityReact.render(React.createElement(Slider,{min:1,max:1}))`, /range/],
    [`const { React, Button }=JavityReact; JavityReact.render(React.createElement(Button,{checked:true}))`, /checked must be a boolean/],
  ];
  for (const [source, message] of cases) assert.throws(() => vm.runInContext(source, loadHost().context), message);
});

test('Text forwards only a boolean opt-in wrap prop to the native host', () => {
  const host = loadHost();
  vm.runInContext(`
    const { React, Text } = JavityReact;
    JavityReact.render(React.createElement(Text, { id: 'paragraph', wrap: true }, 'alpha beta'));
  `, host.context);
  assert.equal(host.committed.type, 'Text');
  assert.equal(host.committed.props.wrap, true);
  assert.equal(host.committed.props.text, 'alpha beta');
  assert.throws(() => vm.runInContext(`
    const { React, Text } = JavityReact;
    JavityReact.render(React.createElement(Text, { id: 'bad-wrap', wrap: 'yes' }, 'x'));
  `, loadHost().context), /wrap must be a boolean/);
});
