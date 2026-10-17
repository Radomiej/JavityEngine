const examples = {
  voxels: {
    label: 'VOXEL MODULE', title: 'Step into the world.',
    description: 'Explore a chunked voxel landscape in first or third person. Edit blocks, switch tools and follow the shared avatar animation.',
    command: "./gradlew :javity-desktop:run --args='--voxels'", image: 'assets/voxel-fps.webp',
    alt: 'Voxel world demo viewed from first person', guide: 'docs/voxels.md',
    imageAlt: 'First-person view across a voxel landscape in the JavityEngine desktop demo'
  },
  shapes: {
    label: 'SHAPE RENDERING', title: 'Draw with a little more life.',
    description: 'Try antialiased 2D circles, rounded rectangles, polylines and custom shader materials in the desktop demo.',
    command: "./gradlew :javity-desktop:run --args='--shapes'", image: 'assets/shapes-demo.webp',
    alt: 'JavityEngine shape materials demo', guide: 'docs/shapes-and-shaders.md',
    imageAlt: 'JavityEngine Shape Lab showing custom shader materials, a gradient, pulse and stripes'
  },
  physics: {
    label: 'OPTIONAL BULLET MODULE', title: 'Give motion some weight.',
    description: 'A separate desktop demo for Bullet rigid bodies, a capsule controller and voxel chunk collision support.',
    command: "./gradlew :javity-desktop:run --args='--physics3d'", image: 'assets/physics3d-demo.webp',
    alt: 'JavityEngine Bullet 3D third-person demo', guide: 'docs/physics3d.md',
    imageAlt: 'Third-person player and dynamic objects in the JavityEngine Bullet 3D demo'
  },
  map: {
    label: 'LINUX X64 · MAPLIBRE NATIVE', title: 'A real map in the scene.',
    description: 'Render a local vector-tile package with MapLibre Native through the optional Linux x64 EGL/GLES adapter.',
    command: "./gradlew :javity-desktop:run --args='--gorzow'", image: 'assets/gorzow-map.webp',
    alt: 'Night-style vector map of Gorzów Wielkopolski', guide: 'docs/gorzow-offline-map.md',
    imageAlt: 'Night-style vector map of Gorzów Wielkopolski displayed by the MapLibre Native demo'
  },
  relay: {
    label: 'JAVA GAMEPLAY · OPTIONAL REACT HUD', title: 'A game, not a framework tour.',
    description: 'Relay Ward combines scene changes, audio, feedback, animation and an optional native React/Yoga HUD.',
    command: "./gradlew :javity-desktop:run --args='--mm'", image: 'assets/relay-ward-demo.webp',
    alt: 'Relay Ward game demo with its native HUD', guide: 'docs/game-authoring.md',
    imageAlt: 'Relay Ward demo showing its gameplay HUD, training drone and target arena'
  }
};

const tabs = [...document.querySelectorAll('.example-tab')];
const panel = document.querySelector('#example-panel');
const source = 'https://github.com/Radomiej/JavityEngine/blob/master/';

function selectExample(tab, moveFocus = false) {
  const item = examples[tab.dataset.example];
  if (!item) return;
  for (const button of tabs) {
    const active = button === tab;
    button.classList.toggle('is-active', active);
    button.setAttribute('aria-selected', String(active));
    button.tabIndex = active ? 0 : -1;
  }
  panel.setAttribute('aria-labelledby', tab.id);
  document.querySelector('#example-kicker').textContent = item.label;
  document.querySelector('#example-title').textContent = item.title;
  document.querySelector('#example-description').textContent = item.description;
  document.querySelector('#example-command').textContent = item.command;
  document.querySelector('#example-source').href = source + item.guide;
  document.querySelector('#example-image').src = item.image;
  document.querySelector('#example-image').alt = item.imageAlt;
  document.querySelector('#example-source').setAttribute('aria-label', `Read ${item.title.toLowerCase()} guide`);
  if (moveFocus) tab.focus();
}

for (const [index, tab] of tabs.entries()) {
  tab.addEventListener('click', () => selectExample(tab));
  tab.addEventListener('keydown', event => {
    if (!['ArrowDown', 'ArrowRight', 'ArrowUp', 'ArrowLeft', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    let next = index;
    if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = tabs.length - 1;
    else if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = (index + 1) % tabs.length;
    else next = (index - 1 + tabs.length) % tabs.length;
    selectExample(tabs[next], true);
  });
}

const menuToggle = document.querySelector('.menu-toggle');
const nav = document.querySelector('#site-nav');
menuToggle.addEventListener('click', () => {
  const open = menuToggle.getAttribute('aria-expanded') !== 'true';
  menuToggle.setAttribute('aria-expanded', String(open));
  menuToggle.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
  nav.classList.toggle('is-open', open);
});
nav.addEventListener('click', event => {
  if (event.target.closest('a') && matchMedia('(max-width: 720px)').matches) {
    menuToggle.setAttribute('aria-expanded', 'false');
    menuToggle.setAttribute('aria-label', 'Open navigation');
    nav.classList.remove('is-open');
  }
});

document.querySelector('.copy-button').addEventListener('click', async event => {
  const button = event.currentTarget;
  const snippet = document.querySelector('.code-card pre code').innerText;
  try {
    await navigator.clipboard.writeText(snippet);
    button.textContent = 'Copied';
  } catch {
    button.textContent = 'Select code';
  }
  window.setTimeout(() => { button.textContent = 'Copy'; }, 1400);
});
