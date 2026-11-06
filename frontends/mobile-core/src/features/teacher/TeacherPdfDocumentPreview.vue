<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import {
  getDocument,
  GlobalWorkerOptions,
  type PDFDocumentLoadingTask,
  type PDFDocumentProxy,
  type RenderTask,
} from 'pdfjs-dist/legacy/build/pdf.mjs';
import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url';
import { readPdfPreviewBytes } from '../../shared/components/attachment-pdf-bytes';
import './teacher-pdf-document-preview.pcss';
const props = defineProps<{ url: string; label: string }>();
GlobalWorkerOptions.workerSrc = workerUrl;
const canvas = ref<HTMLCanvasElement | null>(null);
const pageNumber = ref(1);
const pageCount = ref(0);
const ready = ref(false);
const busy = ref(false);
const error = ref<string | null>(null);
let generation = 0;
let renderRevision = 0;
let abort: AbortController | null = null;
let loadingTask: PDFDocumentLoadingTask | null = null;
let document: PDFDocumentProxy | null = null;
let render: RenderTask | null = null;
function cancelRender(): void {
  renderRevision += 1;
  render?.cancel();
  render = null;
  ready.value = false;
  if (canvas.value) {
    canvas.value.width = 0;
    canvas.value.height = 0;
  }
}
function dispose(): void {
  generation += 1;
  abort?.abort();
  abort = null;
  cancelRender();
  if (loadingTask) void loadingTask.destroy().catch(() => undefined);
  loadingTask = null;
  document = null;
  pageCount.value = 0;
  busy.value = false;
}
async function renderPage(): Promise<void> {
  cancelRender();
  const pdf = document,
    run = generation,
    revision = renderRevision,
    number = pageNumber.value;
  if (!pdf) return;
  const current = () => run === generation && revision === renderRevision && pdf === document;
  busy.value = true;
  error.value = null;
  try {
    const page = await pdf.getPage(number);
    if (!current()) return;
    await nextTick();
    const target = canvas.value;
    if (!current() || !target) return;
    const original = page.getViewport({ scale: 1 });
    const width = Math.min(target.parentElement?.clientWidth || 640, 1024);
    const wantedScale = (width / original.width) * Math.min(window.devicePixelRatio || 1, 2);
    const scale = Math.min(
      wantedScale,
      Math.sqrt(2_000_000 / (original.width * original.height)),
      4096 / Math.max(original.width, original.height),
    );
    const viewport = page.getViewport({ scale });
    if (
      !Number.isFinite(viewport.width) ||
      !Number.isFinite(viewport.height) ||
      viewport.width < 1 ||
      viewport.height < 1
    )
      throw new Error('PDF содержит некорректную страницу.');
    target.width = Math.ceil(viewport.width);
    target.height = Math.ceil(viewport.height);
    const task = page.render({ canvas: target, viewport, background: 'white' });
    render = task;
    await task.promise;
    if (!current()) return;
    render = null;
    ready.value = true;
  } catch (cause) {
    if (current())
      error.value = cause instanceof Error ? cause.message : 'Не удалось показать страницу PDF.';
  } finally {
    if (current()) busy.value = false;
  }
}
async function load(): Promise<void> {
  dispose();
  const url = props.url,
    run = generation;
  const current = () => run === generation && url === props.url;
  pageNumber.value = 1;
  busy.value = true;
  error.value = null;
  try {
    if (!url.startsWith('blob:')) throw new Error('Нет безопасного адреса PDF.');
    const controller = new AbortController();
    abort = controller;
    const response = await fetch(url, { signal: controller.signal });
    if (!response.ok) throw new Error('Не удалось прочитать PDF.');
    const bytes = await readPdfPreviewBytes(response, controller.signal);
    if (!current()) return;
    const task = getDocument({
      data: bytes,
      stopAtErrors: true,
      maxImageSize: 16_000_000,
      useWasm: false,
      useSystemFonts: true,
      disableAutoFetch: true,
      disableStream: true,
    });
    loadingTask = task;
    const pdf = await task.promise;
    if (!current()) {
      await task.destroy();
      return;
    }
    if (pdf.numPages < 1 || pdf.numPages > 1000)
      throw new Error('PDF содержит недопустимое количество страниц.');
    document = pdf;
    pageCount.value = pdf.numPages;
    await renderPage();
  } catch (cause) {
    if (current()) {
      dispose();
      error.value = cause instanceof Error ? cause.message : 'Не удалось открыть PDF.';
    }
  }
}
function navigate(delta: number): void {
  const number = pageNumber.value + delta;
  if (number < 1 || number > pageCount.value) return;
  pageNumber.value = number;
  void renderPage();
}
watch(
  () => props.url,
  () => {
    void load();
  },
  { immediate: true, flush: 'sync' },
);
onBeforeUnmount(dispose);
</script>
<template>
  <section
    class="teacher-pdf-document"
    :aria-label="label"
    :aria-busy="busy"
  >
    <nav
      v-if="pageCount"
      class="teacher-pdf-document__navigation"
      aria-label="Страницы PDF"
    >
      <button
        type="button"
        class="teacher-screen__secondary"
        :disabled="pageNumber <= 1"
        aria-label="Предыдущая страница PDF"
        @click="navigate(-1)"
      >
        Назад
      </button>
      <span
        role="status"
        aria-live="polite"
      >Страница {{ pageNumber }} из {{ pageCount }}</span>
      <button
        type="button"
        class="teacher-screen__secondary"
        :disabled="pageNumber >= pageCount"
        aria-label="Следующая страница PDF"
        @click="navigate(1)"
      >
        Далее
      </button>
    </nav>
    <p
      v-if="busy"
      role="status"
    >
      Показываем страницу PDF…
    </p>
    <canvas
      v-show="ready"
      ref="canvas"
      role="img"
      :aria-label="`${label}. Страница ${pageNumber} из ${pageCount}`"
    />
    <p
      v-if="error"
      role="status"
    >
      {{ error }}
      <button
        type="button"
        class="teacher-screen__secondary"
        @click="document ? renderPage() : load()"
      >
        Повторить
      </button>
    </p>
  </section>
</template>
