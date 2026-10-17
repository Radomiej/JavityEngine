(async () => {
  const fixture = await (await fetch("k1-controls-fixture.json")).json();
  const root = document.documentElement;
  root.style.setProperty("--k1-viewport-width", `${fixture.viewport.width}px`);
  root.style.setProperty("--k1-viewport-height", `${fixture.viewport.height}px`);

  const panel = document.createElement("section");
  panel.id = fixture.panel.id;
  panel.className = fixture.panel.classes;
  panel.style.setProperty("--k1-panel-left", `${fixture.panel.rect.x}px`);
  panel.style.setProperty("--k1-panel-top", `${fixture.panel.rect.y}px`);

  for (const item of fixture.panel.children) {
    let element;
    if (item.type === "Text") {
      element = document.createElement("div");
      element.textContent = item.text;
    } else if (item.type === "Toggle") {
      element = document.createElement("input");
      element.type = "checkbox";
      element.checked = item.checked;
      element.setAttribute("role", "switch");
      element.setAttribute("aria-checked", String(item.checked));
      element.disabled = item.disabled;
      element.classList.add("k1-switch");
    } else if (item.type === "Slider") {
      element = document.createElement("input");
      element.type = "range";
      element.min = item.min;
      element.max = item.max;
      element.step = item.step;
      element.value = item.value;
      element.disabled = item.disabled;
      element.classList.add("k1-range");
    } else {
      throw new Error(`Unsupported fixture node: ${item.type}`);
    }

    element.id = item.id;
    element.classList.add(...item.classes.split(/\s+/));
    if (item.type === "Slider") {
      const wrapper = document.createElement("div");
      const sizingClasses = item.classes
        .split(/\s+/)
        .filter(token => token.startsWith("w-[") || token.startsWith("h-[") || token === "flex-none");
      wrapper.className = `k1-range-wrapper ${sizingClasses.join(" ")}`;
      wrapper.style.setProperty("--k1-slider-ratio", "0");
      wrapper.append(element);

      const track = document.createElement("i");
      track.className = "k1-range-track";
      const fill = document.createElement("i");
      fill.className = "k1-range-fill";
      const knob = document.createElement("i");
      knob.className = "k1-range-knob";
      wrapper.append(track, fill, knob);

      const updateSliderRatio = () => {
        const min = element.min === "" ? 0 : Number(element.min);
        const max = element.max === "" ? 100 : Number(element.max);
        const value = element.valueAsNumber;
        const ratio = max > min ? Math.max(0, Math.min(1, (value - min) / (max - min))) : 0;
        wrapper.style.setProperty("--k1-slider-ratio", String(ratio));
      };
      element.addEventListener("input", updateSliderRatio);
      element.addEventListener("change", updateSliderRatio);
      updateSliderRatio();
      panel.append(wrapper);
    } else {
      panel.append(element);
    }
  }

  document.querySelector("#fixture").append(panel);
  await document.fonts.ready;
  if (!document.fonts.check('16px "Open Sans"')) throw new Error("Open Sans did not load");

  const panelRect = panel.getBoundingClientRect();
  const authoredRect = fixture.panel.rect;
  for (const key of ["x", "y", "width", "height"]) {
    if (Math.abs(panelRect[key] - authoredRect[key]) > 0.02) {
      throw new Error(`Panel ${key} disagrees with fixture rect: ${panelRect[key]} vs ${authoredRect[key]}`);
    }
  }
  if (document.querySelector("#fixture").getBoundingClientRect().width !== fixture.viewport.width ||
      document.querySelector("#fixture").getBoundingClientRect().height !== fixture.viewport.height) {
    throw new Error("Browser fixture viewport disagrees with shared fixture");
  }

  window.k1FixtureReady = true;
})();
