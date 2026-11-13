import { nextTick, ref, type Ref } from 'vue';

interface Point {
  x: number;
  y: number;
}

const minZoom = 1;
const maxZoom = 4;

export function useMapViewport(
  viewport: Ref<HTMLElement | null>,
  ready: Readonly<Ref<boolean>>,
  suspendHostSwipes: () => (() => void) | undefined,
) {
  const zoom = ref(minZoom);
  const pointers = new Map<number, Point>();
  let pointerType: string | undefined;
  let releaseHostSwipes: (() => void) | undefined;
  let pendingScroll: { area: HTMLElement; point: Point } | undefined;

  function scrollPosition(area: HTMLElement): Point {
    return pendingScroll?.area === area
      ? pendingScroll.point
      : { x: area.scrollLeft, y: area.scrollTop };
  }

  function scrollTo(area: HTMLElement, point: Point, scale: number): void {
    const target = {
      x: Math.max(0, Math.min((scale - 1) * area.clientWidth, point.x)),
      y: Math.max(0, Math.min((scale - 1) * area.clientHeight, point.y)),
    };
    if (scale === zoom.value && !pendingScroll) {
      area.scrollTo(target.x, target.y);
      return;
    }

    // Vue must resize the image before the browser can scroll to the new extent.
    if (pendingScroll?.area === area) {
      pendingScroll.point = target;
    } else {
      const update = { area, point: target };
      pendingScroll = update;
      void nextTick(() => {
        if (pendingScroll !== update) return;
        pendingScroll = undefined;
        if (viewport.value === area) area.scrollTo(update.point.x, update.point.y);
      });
    }
    zoom.value = scale;
  }

  function changeView(area: HTMLElement, scale: number, before: Point, after: Point): void {
    const bounded = Math.max(minZoom, Math.min(maxZoom, scale));
    const ratio = bounded / zoom.value;
    const scroll = scrollPosition(area);
    scrollTo(
      area,
      { x: (scroll.x + before.x) * ratio - after.x, y: (scroll.y + before.y) * ratio - after.y },
      bounded,
    );
  }

  function stopPointers(): void {
    const area = viewport.value;
    const ids = [...pointers.keys()];
    pointers.clear();
    pointerType = undefined;
    for (const id of ids) {
      if (area?.hasPointerCapture(id)) area.releasePointerCapture(id);
    }
    releaseHostSwipes?.();
    releaseHostSwipes = undefined;
  }

  function resetView(): void {
    stopPointers();
    pendingScroll = undefined;
    zoom.value = minZoom;
    viewport.value?.scrollTo(0, 0);
  }

  function zoomBy(delta: number): void {
    const area = viewport.value;
    if (!area || !ready.value) return;
    stopPointers();
    const center = { x: area.clientWidth / 2, y: area.clientHeight / 2 };
    changeView(area, zoom.value + delta, center, center);
  }

  function onMapKey(event: KeyboardEvent): void {
    const area = viewport.value;
    if (!area || !ready.value || event.altKey || event.ctrlKey || event.metaKey) return;
    if (event.key === '+' || event.key === '=') zoomBy(0.25);
    else if (event.key === '-' || event.key === '−') zoomBy(-0.25);
    else if (
      zoom.value > minZoom &&
      ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)
    ) {
      const horizontal = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0;
      const vertical = event.key === 'ArrowUp' ? -1 : event.key === 'ArrowDown' ? 1 : 0;
      const scroll = scrollPosition(area);
      scrollTo(
        area,
        {
          x: scroll.x + (horizontal * area.clientWidth) / 8,
          y: scroll.y + (vertical * area.clientHeight) / 8,
        },
        zoom.value,
      );
    } else return;
    event.preventDefault();
  }

  function onMapWheel(event: WheelEvent): void {
    if (!ready.value || (!event.ctrlKey && !event.metaKey) || event.deltaY === 0) return;
    event.preventDefault();
    zoomBy(event.deltaY < 0 ? 0.25 : -0.25);
  }

  function startPointer(event: PointerEvent): void {
    const area = viewport.value;
    const touch = event.pointerType === 'touch';
    if (
      !area ||
      !ready.value ||
      (pointerType !== undefined && pointerType !== event.pointerType) ||
      (!touch && (zoom.value === minZoom || event.button !== 0 || pointers.size > 0))
    )
      return;

    if (touch && !pointers.size) releaseHostSwipes = suspendHostSwipes();
    pointerType = event.pointerType;
    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    area.setPointerCapture(event.pointerId);
    event.preventDefault();
  }

  function gesture() {
    const [first, second] = pointers.values();
    if (!first) return undefined;
    if (!second) return { center: first, distance: 0 };
    return {
      center: { x: (first.x + second.x) / 2, y: (first.y + second.y) / 2 },
      distance: Math.hypot(first.x - second.x, first.y - second.y),
    };
  }

  function movePointer(event: PointerEvent): void {
    const area = viewport.value;
    if (!area || !pointers.has(event.pointerId)) return;
    const before = gesture();
    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    const after = gesture();
    if (!before || !after) return;
    if (pointers.size > 1) {
      const { left, top } = area.getBoundingClientRect();
      changeView(
        area,
        before.distance > 0 ? (zoom.value * after.distance) / before.distance : zoom.value,
        { x: before.center.x - left, y: before.center.y - top },
        { x: after.center.x - left, y: after.center.y - top },
      );
    } else if (zoom.value > minZoom) {
      changeView(area, zoom.value, before.center, after.center);
    }
    event.preventDefault();
  }

  function endPointer(event: PointerEvent): void {
    if (!pointers.delete(event.pointerId)) return;
    const area = viewport.value;
    if (area?.hasPointerCapture(event.pointerId)) area.releasePointerCapture(event.pointerId);
    if (pointers.size === 0) stopPointers();
  }

  function fittedImage(area: HTMLElement, imageRatio: number) {
    const width = Math.min(area.clientWidth, area.clientHeight * imageRatio);
    const height = width / imageRatio;
    return { width, height };
  }

  function readCenter(imageRatio: number): Point | undefined {
    const area = viewport.value;
    if (!area?.clientWidth || !area.clientHeight) return undefined;
    const { width, height } = fittedImage(area, imageRatio);
    const scroll = scrollPosition(area);
    pendingScroll = undefined;
    // Keep the same point on the fitted image when the viewer changes aspect ratio.
    return {
      x: 0.5 + (scroll.x - ((zoom.value - 1) * area.clientWidth) / 2) / (width * zoom.value),
      y: 0.5 + (scroll.y - ((zoom.value - 1) * area.clientHeight) / 2) / (height * zoom.value),
    };
  }

  function restoreCenter(center: Point | undefined, imageRatio: number): void {
    const area = viewport.value;
    if (!area || !center) return;
    const { width, height } = fittedImage(area, imageRatio);
    const scale = zoom.value;
    scrollTo(
      area,
      {
        x: (center.x - 0.5) * width * scale + ((scale - 1) * area.clientWidth) / 2,
        y: (center.y - 0.5) * height * scale + ((scale - 1) * area.clientHeight) / 2,
      },
      scale,
    );
  }

  return {
    zoom,
    resetView,
    stopPointers,
    zoomBy,
    onMapKey,
    onMapWheel,
    startPointer,
    movePointer,
    endPointer,
    readCenter,
    restoreCenter,
  };
}
