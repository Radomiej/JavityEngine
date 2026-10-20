const examples = {
  quality: {
    label: 'OPTIONAL glTF / PBR / HDR', title: 'One garden. Three moods.',
    description: 'Original imported Fox models, PBR foliage, directional shadows and HDR exposure share one scene. Switch afternoon, last light and lantern presets; open the runtime inspector with F1.',
    command: './gradlew :javity-desktop:qualityDemo', image: 'assets/fox-garden-day.webp',
    guide: 'docs/quality-showcase.md',
    imageAlt: 'Two animated Fox models in the JavityEngine desktop garden showcase',
    credit: 'Fox: PixelMannen (CC0); tomkranis / AsoboStudio / scurest (CC BY 4.0).',
    notice: 'tools/animation-validation/fox/NOTICE.md'
  },
  foliage: {
    label: 'STATIC FOLIAGE PATCHES', title: 'Build a little woodland.',
    description: 'Original grass, shrubs and branching trees meet ordinary scene lighting and fog. Deterministic patches merge the plants into meshes; use keys 1–3 for wide, close and dusk views.',
    command: './gradlew :javity-desktop:foliage', image: 'assets/foliage-path.webp',
    guide: 'docs/foliage.md',
    imageAlt: 'A path through original trees, grass and shrubs in the JavityEngine desktop foliage demo',
    credit: 'Original foliage atlas and demo tree geometry: CC0 1.0.',
    notice: 'javity-core/src/main/resources/foliage/NOTICE.md'
  },
  avatar: {
    label: 'EXPLICIT IMPORTED AVATAR', title: 'One rig. Your point of view.',
    description: 'An original CC0 block humanoid demonstrates explicit body regions, independent poses and a hand-tool socket. Space switches to camera-local FPS arms; T toggles the tool. A small authoring fixture with a documented scope.',
    command: './gradlew :javity-desktop:importedAvatar', image: 'assets/avatar-full.webp',
    guide: 'docs/avatars.md',
    imageAlt: 'Full-body and independent arms and tool instances of the original imported block humanoid fixture',
    credit: 'Original block humanoid, rig, hand tool and animation: CC0 1.0.',
    notice: 'javity-gltf/src/main/resources/org/javity/gltf/fixtures/LICENSE.txt'
  },
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
  const credit = document.querySelector('#example-credit');
  credit.replaceChildren();
  credit.hidden = !item.credit;
  if (item.credit) {
    credit.append(document.createTextNode(item.credit + ' '));
    const notice = document.createElement('a');
    notice.href = source + item.notice;
    notice.textContent = 'Asset notice ↗';
    notice.target = '_blank';
    notice.rel = 'noreferrer';
    credit.append(notice);
  }
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

// Native dialog keeps focus inside the enlarged capture and supports Escape.
const galleryDialog = document.querySelector('#gallery-dialog');
const galleryImage = document.querySelector('#gallery-image');
const galleryCaption = document.querySelector('#gallery-caption');
let galleryTrigger = null;
for (const button of document.querySelectorAll('[data-gallery-open]')) {
  button.addEventListener('click', () => {
    const thumbnail = button.querySelector('img');
    if (typeof galleryDialog.showModal !== 'function') {
      window.location.href = thumbnail.src;
      return;
    }
    galleryTrigger = button;
    galleryImage.src = thumbnail.src;
    galleryImage.alt = thumbnail.alt;
    const card = button.closest('.gallery-card');
    const title = card.querySelector('figcaption b').textContent;
    const detail = card.querySelector('figcaption small').textContent;
    const credit = thumbnail.src.includes('fox-garden')
      ? 'Fox: PixelMannen (CC0); rig / animation: tomkranis; conversion: AsoboStudio / scurest (CC BY 4.0).'
      : thumbnail.src.includes('avatar')
        ? 'Original block humanoid, rig and tool animation: CC0 1.0.'
        : 'Original foliage artwork and tree geometry: CC0 1.0.';
    galleryCaption.textContent = `${title} · ${detail}. Desktop engine capture. ${credit}`;
    galleryDialog.showModal();
    document.body.classList.add('gallery-is-open');
  });
}
document.querySelector('#gallery-close').addEventListener('click', () => galleryDialog.close());
galleryDialog.addEventListener('close', () => {
  document.body.classList.remove('gallery-is-open');
  galleryTrigger?.focus({ preventScroll: true });
});
galleryDialog.addEventListener('click', event => {
  if (event.target !== galleryDialog) return;
  const bounds = galleryDialog.getBoundingClientRect();
  if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) galleryDialog.close();
});
