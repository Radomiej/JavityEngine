import * as maplibregl from '/vendor/maplibre-gl.mjs';

(() => {
  const errors = [];
  window.__mapErrors = errors;
  const query = new URLSearchParams(location.search);
  const styleName = query.get('style') || 'day';
  if (styleName !== 'day' && styleName !== 'night') {
    errors.push(`Unsupported style query: ${styleName}`);
  }

  const manifestRequest = fetch('/map.json').then((response) => {
    if (!response.ok) throw new Error(`Manifest request failed: ${response.status}`);
    return response.json();
  });

  manifestRequest.then((manifest) => {
    const camera = manifest.initialCamera;
    const map = new maplibregl.Map({
      container: 'map',
      style: `/style/${styleName}`,
      center: [camera.longitude, camera.latitude],
      zoom: camera.zoom,
      bearing: camera.bearing,
      pitch: camera.pitch,
      attributionControl: false,
      preserveDrawingBuffer: true,
      antialias: true
    });
    window.__map = map;
    map.on('error', (event) => {
      const detail = event && event.error ? event.error.message : String(event);
      errors.push(detail);
      console.error('MapLibre error:', detail);
    });
    map.once('load', () => {
      map.once('idle', () => {
        window.__referenceReady = {
          style: styleName,
          version: maplibregl.getVersion(),
          camera: map.getCenter().toArray().concat(map.getZoom()),
          loaded: map.loaded()
        };
      });
    });
  }).catch((error) => {
    errors.push(error.stack || String(error));
    console.error(error);
  });
})();
