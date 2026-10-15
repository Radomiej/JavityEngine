(async () => {
  const fixture = await (await fetch("mm-fixture.json")).json();
  const stateName = new URLSearchParams(location.search).get("state") || "idle";
  const state = fixture.states[stateName];
  if (!state) throw new Error(`Unknown MM state: ${stateName}`);
  const values = {
    health: state.health,
    healthPercent: `${Math.round(state.health * 100)}%`,
    healthNumber: `${Math.round(state.health * 100)} / 100`,
    generation: stateName === "arena" ? "GEN 2" : "GEN 1",
    droneState: state.droneState,
    canFire: state.canFire,
    canDeploy: state.canDeploy,
    canCancel: state.canCancel,
    loadProgress: state.loadProgress,
    loadStatus: state.loadStatus,
    volume: state.volume,
    volumePercent: `${Math.round(state.volume * 100)}%`,
    muted: state.muted
  };
  const bounds = {};
  const rgb = (color) => {
    const canvas = document.createElement("canvas");
    canvas.width = canvas.height = 1;
    const context = canvas.getContext("2d");
    context.fillStyle = color;
    context.fillRect(0, 0, 1, 1);
    return Array.from(context.getImageData(0, 0, 1, 1).data).slice(0, 3);
  };
  const tags = { View: "div", Text: "div", Button: "button", Image: "img", Progress: "div", Toggle: "button", Slider: "div" };
  function render(node) {
    const element = document.createElement(tags[node.type]);
    element.id = node.key;
    element.className = `${node.classes || ""} mm-${node.type.toLowerCase()}`;
    if (node.type === "View") element.classList.add("flex");
    if (node.type === "Text") element.textContent = node.bind ? values[node.bind] : node.text;
    if (node.type === "Image") {
      element.alt = "";
      element.dataset.source = node.props.source;
      element.src = node.props.source;
      element.style.objectFit = node.props.region ? "cover" : (node.props.fit || "contain");
      if (node.props.region) element.style.objectPosition = "left center";
    }
    if (node.type === "Button") {
      element.type = "button";
      element.textContent = node.props.text;
      element.disabled = node.props.disabledSignal ? !values[node.props.disabledSignal] : false;
    }
    if (node.type === "Progress") {
      const raw = values[node.props.valueSignal];
      const checkpoint = state.progress?.[node.key];
      const displayed = checkpoint?.displayed ?? raw;
      const trail = checkpoint?.trail ?? displayed;
      element.setAttribute("role", "progressbar");
      element.setAttribute("aria-valuenow", String(raw));
      const trailElement = document.createElement("i");
      const direction = raw < displayed ? "loss" : "gain";
      trailElement.className = `mm-progress-trail ${trail > displayed ? direction : ""}`;
      trailElement.style.width = `${trail * 100}%`;
      const fill = document.createElement("i");
      fill.className = "mm-progress-fill";
      fill.style.width = `${displayed * 100}%`;
      element.append(trailElement, fill);
    }
    if (node.type === "Toggle") {
      element.type = "button";
      element.classList.add("mm-toggle");
      element.setAttribute("role", "switch");
      element.setAttribute("aria-checked", String(values[node.props.checkedSignal]));
      element.append(document.createElement("i"));
      element.firstElementChild.className = "mm-toggle-knob";
    }
    if (node.type === "Slider") {
      element.classList.add("mm-range-wrapper");
      const input = document.createElement("input");
      input.id = `${node.key}-input`;
      input.type = "range";
      input.min = String(node.props.min); input.max = String(node.props.max); input.step = String(node.props.step);
      input.value = String(values[node.props.valueSignal]);
      input.className = "mm-range";
      const track = document.createElement("i"); track.className = "mm-range-track";
      const fill = document.createElement("i"); fill.className = "mm-range-fill";
      const knob = document.createElement("i"); knob.className = "mm-range-knob";
      element.append(input, track, fill, knob);
      const ratio = (values[node.props.valueSignal] - node.props.min) / (node.props.max - node.props.min);
      element.style.setProperty("--range-ratio", String(ratio));
    }
    for (const child of node.children || []) element.append(render(child));
    const rect = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    bounds[node.key] = {
      rect: {x: rect.x, y: rect.y, width: rect.width, height: rect.height},
      text: node.type === "Text" ? element.textContent : undefined,
      font: style.fontFamily,
      fontSize: style.fontSize,
      color: style.color,
      background: style.backgroundColor
      ,colorRgb: rgb(style.color)
    };
    return element;
  }
  const root = render(fixture.tree);
  const image = root.querySelector("img");
  image.classList.add("mm-image");
  document.querySelector("#mm-stage").append(root);
  for (const wrapper of root.querySelectorAll(".mm-range-wrapper")) {
    const width = wrapper.getBoundingClientRect().width;
    const ratio = Number(wrapper.style.getPropertyValue("--range-ratio"));
    const activeWidth = Math.max(0, (width - 18) * ratio);
    wrapper.querySelector(".mm-range-fill").style.width = `${activeWidth}px`;
    wrapper.querySelector(".mm-range-knob").style.left = `${activeWidth}px`;
  }
  if (state.focus) document.getElementById(`${state.focus}-input`)?.focus();
  await image.decode();
  await document.fonts.ready;
  await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  window.mmReady = true;
  window.mmMetrics = () => ({
    state: stateName,
    viewport: {width: innerWidth, height: innerHeight, dpr: devicePixelRatio},
    values: state,
    openSansLoaded: document.fonts.check('14px "Open Sans"'),
    controlShapes: (() => {
      const rect = (element) => { const r = element.getBoundingClientRect(); return {x:r.x, y:r.y, width:r.width, height:r.height}; };
      const toggle = document.getElementById("mute-toggle");
      const toggleRect = rect(toggle);
      const toggleTrack = getComputedStyle(toggle, "::before");
      const toggleKnob = getComputedStyle(toggle, "::after");
      const trackWidth = parseFloat(toggleTrack.width), trackHeight = parseFloat(toggleTrack.height);
      const knobWidth = parseFloat(toggleKnob.width), knobHeight = parseFloat(toggleKnob.height);
      const trackX = toggleRect.x + (toggleRect.width - trackWidth) / 2;
      const trackY = toggleRect.y + (toggleRect.height - trackHeight) / 2;
      const checked = toggle.getAttribute("aria-checked") === "true";
      const knobX = checked ? trackX + trackWidth - knobWidth - 2 : trackX + 2;
      const knobY = trackY + 2;
      const range = document.getElementById("volume-slider");
      return {
        toggleTrack: {x:trackX, y:trackY, width:trackWidth, height:trackHeight},
        toggleKnob: {x:knobX, y:knobY, width:knobWidth, height:knobHeight},
        sliderTrack: rect(range.querySelector(".mm-range-track")),
        sliderFill: rect(range.querySelector(".mm-range-fill")),
        sliderKnob: rect(range.querySelector(".mm-range-knob"))
      };
    })(),
    elements: Object.fromEntries(Object.keys(bounds).map(key => {
      const element = document.getElementById(key);
      if (!element) return [key, bounds[key]];
      const rect = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      const textStyle = element.classList.contains("mm-text") ? style : null;
      return [key, {rect: {x: rect.x, y: rect.y, width: rect.width, height: rect.height},
        text: textStyle ? element.textContent : undefined,
        font: textStyle ? style.fontFamily : undefined, fontSize: textStyle ? style.fontSize : undefined,
        color: style.color, colorRgb: rgb(style.color), background: style.backgroundColor,
        progress: element.classList.contains("mm-progress") ? {
          displayed: (() => { const r = element.querySelector(".mm-progress-fill").getBoundingClientRect(); return {x:r.x,y:r.y,width:r.width,height:r.height}; })(),
          trail: (() => { const r = element.querySelector(".mm-progress-trail").getBoundingClientRect(); return {x:r.x,y:r.y,width:r.width,height:r.height}; })()
        } : undefined}];
    }))
  });
})();
