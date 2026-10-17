(()=>{
  const React = JavityReact.React;
  const {View, Text, Button} = JavityReact;
  function AndroidHud() {
    const [score, setScore] = React.useState(() => JavityGame.get("score"));
    const [layout, setLayout] = React.useState(() => JavityGame.get("compactUi"));
    React.useEffect(() => {
      const scoreSubscription = JavityGame.subscribe("score", setScore);
      const layoutSubscription = JavityGame.subscribe("compactUi", setLayout);
      return () => {
        JavityGame.unsubscribe(scoreSubscription);
        JavityGame.unsubscribe(layoutSubscription);
      };
    }, []);
    const compact = layout !== "portrait";
    const short = layout === "compact-short";
    const hudProps = short
      ? {id: "android-hud", className: "flex-row items-center gap-2 px-2 py-1 bg-slate-900 rounded-lg border border-cyan-400"}
      : compact
        ? {id: "android-hud", className: "flex-row items-center gap-2 px-2 py-3 bg-slate-900 rounded-lg border border-cyan-400"}
        : {id: "android-hud", className: "flex-col gap-2 p-3 bg-slate-900 rounded-lg border border-cyan-400"};
    const incrementProps = short
      ? {id: "android-increment", className: "h-6 text-sm bg-blue-600 text-white", text: "Increment", onClick: () => { JavityGame.call("androidIncrement"); }}
      : compact
        ? {id: "android-increment", className: "h-10 text-sm bg-blue-600 text-white", text: "Increment", onClick: () => { JavityGame.call("androidIncrement"); }}
        : {id: "android-increment", className: "h-11 bg-blue-600 text-white", text: "Increment in Java", onClick: () => { JavityGame.call("androidIncrement"); }};
    return React.createElement(
      View,
      hudProps,
      compact ? null : React.createElement(Text, {id: "android-title", className: "text-base text-cyan-400"}, "JAVITY · ANDROID / GLES 3"),
      compact
        ? React.createElement(Text, {id: "android-score-value", className: "text-sm text-white"}, `Java signal: ${score}`)
        : React.createElement(Text, {id: "android-score-value", className: "text-lg text-white"}, `Java signal: ${score}`),
      React.createElement(Button, incrementProps)
    );
  }
  JavityReact.render(React.createElement(AndroidHud));
})();
