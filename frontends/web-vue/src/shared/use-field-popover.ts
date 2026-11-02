import { nextTick, onBeforeUnmount, onMounted, ref, type Ref } from 'vue';

/** Top-layer fields share viewport placement while retaining local control state. */
export function useFieldPopover(
  trigger: Ref<HTMLElement | null>,
  panel: Ref<HTMLElement | null>,
  focusContent: () => void,
  matchWidth = false,
  fieldRoot?: Ref<HTMLElement | null>,
) {
  const expanded = ref(false);
  function place() {
    const anchor = trigger.value;
    const popup = panel.value;
    if (!anchor || !popup?.matches(':popover-open')) return;
    const bounds = anchor.getBoundingClientRect();
    const edge = parseFloat(getComputedStyle(document.documentElement).fontSize) / 2;
    const viewportWidth = document.documentElement.clientWidth;
    const viewportHeight = window.innerHeight;
    if (bounds.bottom < 0 || bounds.top > viewportHeight) {
      close(false);
      return;
    }
    if (matchWidth) popup.style.inlineSize = `${Math.min(bounds.width, viewportWidth - edge * 2)}px`;
    popup.style.maxBlockSize = `${viewportHeight - edge * 2}px`;
    const size = popup.getBoundingClientRect();
    const below = Math.max(0, viewportHeight - bounds.bottom - edge * 2);
    const above = Math.max(0, bounds.top - edge * 2);
    const opensBelow = size.height <= below || below >= above;
    const available = opensBelow ? below : above;
    popup.style.left = `${Math.max(edge, Math.min(bounds.left, viewportWidth - size.width - edge))}px`;
    popup.style.top = `${opensBelow ? Math.max(edge, bounds.bottom + edge) : Math.max(edge, bounds.top - edge - Math.min(size.height, available))}px`;
    popup.style.maxBlockSize = `${available}px`;
  }
  function open() {
    panel.value?.showPopover();
    place();
  }
  function close(restoreFocus = true) {
    if (panel.value?.matches(':popover-open')) panel.value.hidePopover();
    if (restoreFocus) trigger.value?.focus({ preventScroll: true });
  }
  function toggle() {
    if (panel.value?.matches(':popover-open')) close();
    else open();
  }
  function toggled(event: Event) {
    expanded.value = (event as ToggleEvent).newState === 'open';
    if (expanded.value) {
      place();
      void nextTick(focusContent);
    }
  }
  function focusout(event: FocusEvent) {
    const next = event.relatedTarget;
    if (next instanceof Node && !panel.value?.contains(next) && !(fieldRoot?.value ?? trigger.value)?.contains(next)) close(false);
  }
  function outside(event: PointerEvent) {
    if (!panel.value?.matches(':popover-open')) return;
    const path = event.composedPath();
    if (!path.includes(panel.value) && !path.includes(fieldRoot?.value ?? trigger.value!)) close(false);
  }
  function escaped(event: KeyboardEvent) {
    if (event.key !== 'Escape' || !panel.value?.matches(':popover-open')) return;
    event.preventDefault();
    close();
  }
  function scrolled(event: Event) {
    if (event.target instanceof Node && panel.value?.contains(event.target)) return;
    place();
  }
  onMounted(() => {
    window.addEventListener('resize', place);
    window.addEventListener('scroll', scrolled, true);
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escaped);
  });
  onBeforeUnmount(() => {
    window.removeEventListener('resize', place);
    window.removeEventListener('scroll', scrolled, true);
    document.removeEventListener('pointerdown', outside);
    document.removeEventListener('keydown', escaped);
  });
  return { expanded, open, close, toggle, toggled, focusout, place };
}
