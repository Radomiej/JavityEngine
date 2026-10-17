const tags = {View: "div", Text: "p", Button: "button", Progress: "progress", TextField: "input", ScrollView: "div"};
fetch("fixture.json").then(response => response.json()).then(tree => {
  const render = node => {
    const element = document.createElement(tags[node.type]);
    element.id = node.key;
    element.className = node.classes || "";
    const props = node.props || {};
    if (node.type === "Text" || node.type === "Button") element.textContent = props.text || "";
    if (node.type === "TextField") { element.value = props.value || ""; element.type = "text"; }
    if (node.type === "Progress") { element.value = props.value || 0; element.max = props.max || 1; }
    for (const child of node.children || []) element.append(render(child));
    return element;
  };
  document.querySelector("#fixture").append(render(tree));
  const scroll = document.querySelector("#scroll-sample");
  const trackElement = document.createElement("div");
  trackElement.id = "scroll-track";
  const thumbElement = document.createElement("div");
  thumbElement.id = "scroll-thumb";
  document.body.append(trackElement, thumbElement);
  const updateThumb = () => {
    const track = scroll.clientHeight - 4;
    const thumb = Math.max(8, track * scroll.clientHeight / scroll.scrollHeight);
    const travel = Math.max(0, track - thumb);
    const rect = scroll.getBoundingClientRect();
    const top = rect.top + 2 + (scroll.scrollTop / Math.max(1, scroll.scrollHeight - scroll.clientHeight)) * travel;
    Object.assign(trackElement.style, {left: `${rect.right - 8}px`, top: `${rect.top + 2}px`, width: "6px", height: `${track}px`});
    Object.assign(thumbElement.style, {left: `${rect.right - 8}px`, top: `${top}px`, width: "6px", height: `${thumb}px`});
  };
  scroll.addEventListener("scroll", updateThumb, {passive: true});
  window.addEventListener("resize", updateThumb);
  updateThumb();
});
