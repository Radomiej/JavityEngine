const tags = { View: "div", Image: "img" };
const fitValues = { contain: "contain", cover: "cover", stretch: "fill" };
fetch("k4-image-fixture.json")
  .then(response => response.json())
  .then(async fixture => {
    const render = node => {
      const element = document.createElement(tags[node.type]);
      element.id = node.key;
      element.className = node.classes || "";
      if (node.type === "Image") {
        element.src = fixture.asset;
        element.dataset.source = node.props.source;
        element.style.objectFit = fitValues[node.props.fit || "contain"];
        element.style.opacity = String(node.alpha ?? 1);
        element.alt = "";
      }
      for (const child of node.children || []) element.append(render(child));
      return element;
    };
    document.querySelector("#k4-fixture").append(render(fixture.tree));
    await Promise.all([...document.querySelectorAll("img")].map(image => image.decode()));
    const round = value => Math.round(value * 100) / 100;
    const collect = () => Object.fromEntries([...document.querySelectorAll("img")].map(image => {
      const rect = image.getBoundingClientRect();
      const style = getComputedStyle(image);
      return [image.id, {
        rect: {x: round(rect.x), y: round(rect.y), width: round(rect.width), height: round(rect.height)},
        contentRect: {
          x: round(rect.x + parseFloat(style.borderLeftWidth) + parseFloat(style.paddingLeft)),
          y: round(rect.y + parseFloat(style.borderTopWidth) + parseFloat(style.paddingTop)),
          width: round(rect.width - parseFloat(style.borderLeftWidth) - parseFloat(style.borderRightWidth) - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)),
          height: round(rect.height - parseFloat(style.borderTopWidth) - parseFloat(style.borderBottomWidth) - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom))
        },
        fit: style.objectFit,
        opacity: style.opacity,
        borderRadius: style.borderRadius,
        borderWidth: style.borderTopWidth,
        padding: style.paddingTop
      }];
    }));
    window.k4Fixture = fixture;
    window.k4Metrics = () => ({
      viewport: {width: innerWidth, height: innerHeight, dpr: devicePixelRatio},
      images: collect()
    });
    window.applyK4Resize = () => {
      const state = fixture.resize;
      const image = document.getElementById(state.key);
      image.className = state.classes;
      image.style.objectFit = fitValues[state.fit];
      image.dataset.source = state.source;
      return new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    };
    window.k4Ready = true;
  });
