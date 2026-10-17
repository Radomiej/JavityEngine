# Javity React UI host

This package builds the optional React 19 mutation renderer embedded by `javity-script`. It adapts the document-scoped host and ordered tree mutation contracts audited from OneJS and onejs-react to Javity's `UiDocument` API; no Unity or QuickJS bridge is included.

Build the checked-in runtime with Node 20 or newer:

```sh
npm ci
npm run build
```

The build writes `javity-script/src/main/resources/org/javity/script/javity-ui.js` and the separately compiled `javity-sample.js`. The Gradle module uses these checked-in outputs, so Java builds do not require Node or npm. Load the runtime bundle first; it owns the single global `JavityReact` API and React instance. Application TSX should import `JavityReact`, `React`, and hooks from `src/runtime.js`, a small shim over that global, instead of importing React or the host implementation. The sample build bundles that shim but excludes `react` and `react-reconciler`, so evaluating the app bundle cannot replace the host or create a second reconciler. The embedded global is `JavityReact`; JSX intrinsics `div`, `span`, `label`, `button`, `progress`, `input type="text"`, and `scroll` map to the corresponding native controls, with canonical `View`, `Text`, `Button`, `Progress`, `TextField`, and `ScrollView` exports also available. Use `JavityReact.render(React.createElement(...))`, `JavityReact.unmount()`, or the exported intrinsic names `View`, `Text`, `Button`, `Progress`, `TextField`, and `ScrollView`. `sample.tsx` demonstrates hooks and JSX bundling. Plain text becomes `Text` children under `View` and a merged `text` property under `Text`/`Button`.

Only the native control properties in `UiDocument` are accepted. `id` opts into a document key; otherwise each React host instance receives a stable generated key. `className` and `classes` map to the strict `UtilityStyles` catalog. Inline `width`, `height`, `padding`, and `gap` convert finite nonnegative values or `px` strings to catalog utilities. `color` and `backgroundColor` accept hex colors. Other styles and props fail with a clear JavaScript exception; this is not CSS or Tailwind.

`ScriptUiRuntime` owns a single-threaded GraalJS context and borrows its `UiDocument`. `eval` evaluates application JavaScript; `tick(seconds)` advances deterministic timers, animation frames, and queued microtasks; GraalJS drains native Promise jobs at evaluation boundaries. UI callbacks run on the owner thread. The runtime is intended for trusted game UI scripts, not as a security boundary for hostile code. Close unmounts React before clearing callbacks and closing the context.

Each `tick` shares a 10,000-callback limit across due timers, intervals, animation frames, and `queueMicrotask` callbacks. Standalone evaluation and native UI event dispatch use their own bounded host-callback execution. Work that has not started remains queued if the callback limit or an earlier callback exception stops a tick. GraalJS Promise jobs and arbitrary synchronous JavaScript are outside this host callback count, so it is not a time or instruction budget and does not make untrusted scripts safe.

The host implementation is an original adaptation informed by Singtaa/OneJS (`f36ff5e41a16eeddf40e775f28fa7eee856894a0`, MIT) and Singtaa/onejs-react (`51cb714bcedaf5f6adc0bf2601db2920ae19e40d`, npm package 0.4.0, MIT declared in package metadata). Their Unity event, style, and QuickJS implementations are not included. See `LICENSE` for retained MIT notices. React/Reconciler and GraalJS remain separately licensed dependencies.
