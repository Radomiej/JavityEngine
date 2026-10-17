import React from 'react';
import Reconciler from 'react-reconciler';
import ReconcilerConstants from 'react-reconciler/constants';

const { DefaultEventPriority, DiscreteEventPriority } = ReconcilerConstants;
let currentUpdatePriority = DefaultEventPriority;

let nextHandler = 1;
let handlers = new Map();
let pendingHandlers = null;
let nextSignalCallback = 1;
const signalCallbacks = new Map();
const signalSubscriptions = new Map();
const roots = new WeakMap();

// Java receives only names, callback IDs, JSON and primitive return values.
// Guest functions stay in this runtime across both Graal and JNI providers.
globalThis.JavityGame = Object.freeze({
  call(name, ...args) {
    if (args.some(value => value !== null && !['string', 'boolean', 'number'].includes(typeof value))) {
      throw new TypeError('Action arguments must be null, String, Boolean, or Number primitives');
    }
    if (args.some(value => typeof value === 'number' && !Number.isFinite(value))) {
      throw new TypeError('Action number must be finite');
    }
    return JavityNative.gameCall(name, JSON.stringify(args));
  },
  get(name) { return JavityNative.gameGet(name); },
  subscribe(name, callback) {
    if (typeof callback !== 'function') throw new TypeError('signal callback must be a function');
    if (nextSignalCallback >= Number.MAX_SAFE_INTEGER) throw new Error('signal callback id space exhausted');
    const id = `s${nextSignalCallback++}`;
    signalCallbacks.set(id, callback);
    try {
      const subscriptionId = JavityNative.subscribe(name, id);
      signalSubscriptions.set(subscriptionId, id);
      return subscriptionId;
    }
    catch (error) { signalCallbacks.delete(id); throw error; }
  },
  unsubscribe(id) {
    try { JavityNative.unsubscribe(id); }
    finally { releaseSignal(signalSubscriptions.get(id)); }
  },
});
const textInstance = (text) => ({ text: String(text), parent: null });
const isText = (value) => value && typeof value === 'object' && Object.hasOwn(value, 'text') && !value.type;

let nextHostId = 1;
const aliases = Object.freeze({ div: 'View', span: 'Text', label: 'Text', button: 'Button', progress: 'Progress', input: 'TextField', scroll: 'ScrollView', toggle: 'Toggle', slider: 'Slider', img: 'Image' });
function makeInstance(type, props) {
  return { type: aliases[type] || type, key: `host-${nextHostId++}`, props: { ...props }, children: [], parent: null, hidden: false };
}
function detach(child) {
  if (!child?.parent) return;
  const siblings = child.parent.children;
  const index = siblings.indexOf(child);
  if (index >= 0) siblings.splice(index, 1);
  child.parent = null;
}
function append(parent, child, before = null) {
  detach(child);
  const index = before ? parent.children.indexOf(before) : -1;
  if (index < 0) parent.children.push(child); else parent.children.splice(index, 0, child);
  child.parent = parent;
}
function cssPixel(value, name) {
  const number = typeof value === 'number' ? value : (typeof value === 'string' && /^\d+(?:\.\d+)?px$/.test(value) ? Number.parseFloat(value) : NaN);
  if (!Number.isFinite(number) || number < 0) throw new TypeError(`style.${name} must be a nonnegative finite number or px value`);
  return number;
}
function copyProps(host) {
  const props = {};
  const source = host.props || {};
  const classes = [];
  if (source.className != null) { if (typeof source.className !== 'string') throw new TypeError('className must be a string'); classes.push(source.className); }
  if (source.classes != null) { if (typeof source.classes !== 'string') throw new TypeError('classes must be a string'); classes.push(source.classes); }
  if (source.style != null) {
    if (typeof source.style !== 'object' || Array.isArray(source.style)) throw new TypeError('style must be an object');
    for (const [name, value] of Object.entries(source.style)) {
      if (value == null) continue;
      if (name === 'width' || name === 'height' || name === 'padding' || name === 'gap') {
        const token = name === 'width' ? 'w' : name === 'height' ? 'h' : name === 'padding' ? 'p' : 'gap';
        classes.push(`${token}-[${cssPixel(value, name)}px]`);
      } else if (name === 'color' || name === 'backgroundColor') {
        if (typeof value !== 'string' || !/^#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(value)) throw new TypeError(`style.${name} must be a supported hex color`);
        classes.push(`${name === 'color' ? 'text' : 'bg'}-[${value}]`);
      } else throw new TypeError(`Unsupported Javity inline style: ${name}`);
    }
  }
  if (classes.length) props.classes = classes.join(' ');
  for (const name of ['text', 'value', 'checked', 'min', 'max', 'step', 'duration', 'delay', 'disabled', 'wrap']) if (source[name] !== undefined) props[name] = source[name];
  if (host.type === 'Image') {
    if (typeof source.source !== 'string' || !source.source.trim()) throw new TypeError('Image source must be a nonempty registered name string');
    props.source = source.source;
    if (source.fit !== undefined) {
      if (!['contain', 'cover', 'stretch'].includes(source.fit)) throw new TypeError('Image fit must be contain, cover, or stretch');
      props.fit = source.fit;
    }
  }
  if (source.type !== undefined) {
    if (host.type === 'TextField' && source.type === 'text') { /* standard input type */ }
    else throw new TypeError(`Unsupported Javity prop: type=${source.type}`);
  }
  if (props.text !== undefined) props.text = String(props.text);
  if (props.value !== undefined) {
    if (host.type === 'Progress' || host.type === 'Slider') { if (typeof props.value !== 'number' || !Number.isFinite(props.value)) throw new TypeError(`${host.type} value must be a finite number`); }
    else props.value = String(props.value);
  }
  for (const name of ['min', 'max', 'step']) if (props[name] !== undefined && (typeof props[name] !== 'number' || !Number.isFinite(props[name]))) throw new TypeError(`${name} must be a finite number`);
  if (props.step !== undefined && (host.type !== 'Slider' || props.step <= 0)) throw new TypeError('step must be positive and only applies to Slider');
  if (host.type === 'Slider' && (props.min ?? 0) >= (props.max ?? 1)) throw new TypeError('Slider range requires min < max');
  if (host.type === 'Toggle' && props.checked === undefined) throw new TypeError('Toggle requires a controlled checked boolean');
  if (host.type === 'Slider') for (const name of ['min', 'max', 'value', 'step']) if (props[name] === undefined) throw new TypeError(`Slider requires ${name}`);
  for (const name of ['duration', 'delay']) if (props[name] !== undefined && (host.type !== 'Progress' || typeof props[name] !== 'number' || !Number.isFinite(props[name]) || props[name] < 0)) throw new TypeError(`${name} must be a nonnegative finite Progress number`);
  if (props.disabled !== undefined && typeof props.disabled !== 'boolean') throw new TypeError('disabled must be a boolean');
  if (props.wrap !== undefined && (host.type !== 'Text' || typeof props.wrap !== 'boolean')) throw new TypeError('wrap must be a boolean and only applies to Text');
  if (props.checked !== undefined && (host.type !== 'Toggle' || typeof props.checked !== 'boolean')) throw new TypeError('checked must be a boolean and only applies to Toggle');
  if (source.id !== undefined && (typeof source.id !== 'string' || !source.id)) throw new TypeError('id must be a nonempty string');
  const handlerNames = ['onClick', 'onChange', 'onKeyDown', 'onKeyUp', 'onFocus', 'onBlur'];
  for (const name of handlerNames) if (source[name] !== undefined && typeof source[name] !== 'function') throw new TypeError(`${name} must be a function`);
  const allowed = new Set(['children', 'id', 'className', 'classes', 'style', 'text', 'value', 'checked', 'min', 'max', 'step', 'duration', 'delay', 'disabled', 'wrap', 'source', 'fit', ...handlerNames, 'type']);
  for (const name of Object.keys(source)) if (!allowed.has(name)) throw new TypeError(`Unsupported Javity prop: ${name}`);
  const perType = {
    View: new Set(['children']), ScrollView: new Set(['children']),
    Text: new Set(['children', 'text', 'wrap']),
    Button: new Set(['children', 'text', 'disabled', 'onClick', 'onKeyDown', 'onKeyUp', 'onFocus', 'onBlur']),
    Progress: new Set(['children', 'value', 'min', 'max', 'duration', 'delay', 'disabled']),
    TextField: new Set(['children', 'value', 'disabled', 'onChange', 'onKeyDown', 'onKeyUp', 'onFocus', 'onBlur', 'type']),
    Toggle: new Set(['children', 'checked', 'disabled', 'onChange', 'onKeyDown', 'onKeyUp', 'onFocus', 'onBlur']),
    Slider: new Set(['children', 'value', 'min', 'max', 'step', 'disabled', 'onChange', 'onKeyDown', 'onKeyUp', 'onFocus', 'onBlur']),
    Image: new Set(['children', 'source', 'fit']),
  }[host.type];
  if (!perType) throw new TypeError(`Unsupported Javity control: ${host.type}`);
  for (const name of Object.keys(source)) if (name !== 'id' && name !== 'key' && name !== 'className' && name !== 'classes' && name !== 'style' && !perType.has(name)) throw new TypeError(`Unsupported ${host.type} prop: ${name}`);
  for (const name of handlerNames) if (typeof source[name] === 'function') {
    const id = host.handlerIds?.[name] || `h${nextHandler++}`;
    (pendingHandlers || handlers).set(id, source[name]);
    props[name] = id;
  }
  return props;
}
function describe(host, parentKey, index) {
  if (isText(host)) return host.text;
  const key = host.props?.id != null ? String(host.props.id) : host.key == null ? `${parentKey}/${index}` : String(host.key);
  const type = host.type;
  const props = copyProps(host);
  host.handlerIds ||= {};
  for (const name of ['onClick', 'onChange', 'onKeyDown', 'onKeyUp', 'onFocus', 'onBlur']) if (props[name]) host.handlerIds[name] = props[name];
  const children = [];
  const bodyText = [];
  let textIndex = 0;
  for (const child of host.children) {
    if (isText(child)) {
      if (type === 'Text' || type === 'Button') bodyText.push(child.text);
      else children.push({
        type: 'Text', key: `${host.key}:text:${textIndex++}`, classes: '', props: { text: child.text }, children: []
      });
      continue;
    }
    if ((type === 'Text' || type === 'Button') && child.type) {
      throw new TypeError(`${type} accepts text children only`);
    }
    children.push(describe(child, key, children.length));
  }
  if (type === 'Image' && (children.length || bodyText.length)) throw new TypeError('Image cannot have children');
  if (type === 'Text' || type === 'Button') {
    if (bodyText.length) props.text = bodyText.join('');
  }
  const classes = props.classes || '';
  delete props.classes;
  return { type, key, classes, props, children };
}
function collectNativeKeys(node, ids = new Set()) {
  ids.add(node.key);
  for (const child of node.children || []) collectNativeKeys(child, ids);
  return ids;
}
function rootDescriptor(root) {
  const children = root.children.map((child, index) => describe(child, 'root', index));
  if (children.length === 0) return null;
  if (children.length === 1 && typeof children[0] !== 'string') return children[0];
  const normalized = children.map((child, index) => typeof child === 'string' ? {
    type: 'Text', key: `javity-root-text-${index}`, classes: '', props: { text: child }, children: []
  } : child);
  const keys = normalized.reduce((all, node) => collectNativeKeys(node, all), new Set());
  let rootKey = '__javity_react_root__';
  let suffix = 1;
  while (keys.has(rootKey)) rootKey = `__javity_react_root__${suffix++}`;
  return { type: 'View', key: rootKey, classes: '', props: {}, children: normalized };
}
function collectHandlerIds(node, ids = new Set()) {
  for (const value of Object.values(node.props || {})) if (typeof value === 'string' && /^h\d+$/.test(value)) ids.add(value);
  for (const child of node.children || []) collectHandlerIds(child, ids);
  return ids;
}
function commit(root) {
  const previousHandlers = handlers;
  pendingHandlers = new Map();
  try {
    const node = rootDescriptor(root);
    if (!node) {
      JavityNative.commit('{"type":"View","key":"root-empty","classes":"","props":{},"children":[]}', '[]');
      handlers = pendingHandlers;
      return;
    }
    const used = collectHandlerIds(node);
    const handlerIds = [...used].filter(id => pendingHandlers.has(id));
    JavityNative.commit(JSON.stringify(node), JSON.stringify(handlerIds));
    handlers = pendingHandlers;
  } catch (error) {
    handlers = previousHandlers;
    throw error;
  } finally { pendingHandlers = null; }
}

const hostConfig = {
  now: () => JavityScheduler.now(),
  supportsMutation: true,
  supportsPersistence: false,
  supportsHydration: false,
  isPrimaryRenderer: false,
  getRootHostContext: () => null,
  getChildHostContext: () => null,
  getPublicInstance: instance => instance,
  prepareForCommit: () => null,
  resetAfterCommit: root => commit(root),
  createInstance: (type, props) => makeInstance(type, props),
  createTextInstance: textInstance,
  appendInitialChild: append,
  appendChild: append,
  appendChildToContainer: append,
  insertBefore: (parent, child, before) => append(parent, child, before),
  insertInContainerBefore: (parent, child, before) => append(parent, child, before),
  removeChild: (parent, child) => detach(child),
  removeChildFromContainer: (parent, child) => detach(child),
  clearContainer: root => { root.children.forEach(child => { child.parent = null; }); root.children.length = 0; return false; },
  prepareUpdate: () => true,
  commitUpdate: (instance, type, oldProps, newProps) => { instance.props = { ...newProps }; },
  commitTextUpdate: (instance, oldText, newText) => { instance.text = String(newText); },
  hideInstance: instance => { instance.hidden = true; },
  unhideInstance: instance => { instance.hidden = false; },
  hideTextInstance: instance => { instance.hidden = true; },
  unhideTextInstance: instance => { instance.hidden = false; },
  shouldSetTextContent: () => false,
  finalizeInitialChildren: () => false,
  scheduleTimeout: (fn, delay) => setTimeout(fn, delay),
  cancelTimeout: id => clearTimeout(id),
  noTimeout: -1,
  supportsMicrotasks: true,
  scheduleMicrotask: fn => queueMicrotask(fn),
  getCurrentEventPriority: () => 16,
  getCurrentUpdatePriority: () => currentUpdatePriority,
  setCurrentUpdatePriority: priority => { currentUpdatePriority = priority; },
  resolveUpdatePriority: () => currentUpdatePriority || DefaultEventPriority,
  resetFormInstance: () => {},
  commitMount: () => {},
  beforeActiveInstanceBlur: () => {},
  afterActiveInstanceBlur: () => {},
  preparePortalMount: () => {},
  detachDeletedInstance: instance => {
    if (instance?.children) for (const child of instance.children) child.parent = null;
  },
  maySuspendCommit: () => false,
  preloadInstance: () => true,
  startSuspendingCommit: () => {},
  suspendInstance: () => {},
  waitForCommitToBeReady: () => null,
  NotPendingTransition: null,
  HostTransitionContext: { $$typeof: Symbol.for('react.context'), _currentValue: null, _currentValue2: null },
  resetTextContent: () => {},
};
const reconciler = Reconciler(hostConfig);

function getRoot(container = globalThis) {
  if (container !== globalThis) throw new Error('A ScriptUiRuntime owns exactly one React root');
  let root = roots.get(container);
  if (!root) {
    const hostRoot = { children: [], parent: null };
    root = { hostRoot, fiber: reconciler.createContainer(hostRoot, 0, null, false, null, 'javity-ui', error => { throw error; }, error => { throw error; }, error => { throw error; }, null) };
    roots.set(container, root);
  }
  return root;
}
function flush() { reconciler.flushSyncWork(); reconciler.flushPassiveEffects(); }
function discrete(callback, ...args) {
  const previousPriority = currentUpdatePriority;
  currentUpdatePriority = DiscreteEventPriority;
  let result;
  try { reconciler.discreteUpdates(() => { result = callback(...args); }); }
  finally { currentUpdatePriority = previousPriority; }
  if (JavityNative.isOpen()) flush();
  return result;
}
function dispatchSignal(callbackId, value) {
  const callback = signalCallbacks.get(callbackId);
  if (callback) return discrete(callback, value);
}
function releaseSignal(callbackId) {
  if (callbackId === undefined) return;
  signalCallbacks.delete(callbackId);
  for (const [subscriptionId, registeredCallbackId] of signalSubscriptions) {
    if (registeredCallbackId === callbackId) signalSubscriptions.delete(subscriptionId);
  }
}
function dispatchHandler(callbackId, value) {
  const callback = handlers.get(callbackId);
  if (callback) return discrete(callback, value);
}
function dispatchKeyHandler(callbackId, event) {
  const callback = handlers.get(callbackId);
  if (!callback) return false;
  const result = discrete(callback, event);
  return typeof result === 'boolean' ? result : false;
}
function render(node, container = globalThis) {
  const root = getRoot(container);
  reconciler.updateContainerSync(node, root.fiber, null, null);
  flush();
  return node;
}
function unmount(container = globalThis) {
  if (container !== globalThis) throw new Error('A ScriptUiRuntime owns exactly one React root');
  const root = roots.get(container);
  if (!root) return;
  reconciler.updateContainerSync(null, root.fiber, null, null);
  flush();
  roots.delete(container);
  handlers.clear();
  signalCallbacks.clear();
  signalSubscriptions.clear();
  globalThis.__javityClearCallbacks?.();
  pendingHandlers = null;
  JavityNative.commit('{"type":"View","key":"root-empty","classes":"","props":{},"children":[]}', '[]');
}
const api = Object.freeze({
  React, createElement: React.createElement, Fragment: React.Fragment,
  render, unmount, flush, discrete, dispatchSignal, releaseSignal, dispatchHandler, dispatchKeyHandler,
  View: 'View', Text: 'Text', Button: 'Button', Progress: 'Progress', TextField: 'TextField', ScrollView: 'ScrollView', Toggle: 'Toggle', Slider: 'Slider', Image: 'Image'
});
globalThis.JavityReact = api;
export { api as JavityReact };
