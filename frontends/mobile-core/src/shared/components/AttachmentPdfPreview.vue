<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import {
  getDocument,
  GlobalWorkerOptions,
  type PDFDocumentLoadingTask,
  type RenderTask,
} from 'pdfjs-dist/legacy/build/pdf.mjs';
import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url';
import { readPdfPreviewBytes } from './attachment-pdf-bytes';
import './attachment-pdf-preview.pcss';
const props = withDefaults(defineProps<{ url: string; openFile: () => void; label?: string }>(), {
  label: 'Первая страница PDF',
});
GlobalWorkerOptions.workerSrc = workerUrl;
const canvas = ref<HTMLCanvasElement | null>(null);
const ready = ref(false);
const error = ref<string | null>(null);
let generation = 0;
let abort: AbortController | null = null;
let loadingTask: PDFDocumentLoadingTask | null = null;
let render: RenderTask | null = null;
function dispose(): void {
  generation += 1;
  abort?.abort();
  abort = null;
  render?.cancel();
  render = null;
  // LoadingTask owns the worker and document transport, including when parsing is pending.
  if (loadingTask) void loadingTask.destroy().catch(() => undefined);
  loadingTask = null;
  ready.value = false;
  if (canvas.value) {
    canvas.value.width = 0;
    canvas.value.height = 0;
  }
}
watch(
  () => props.url,
  async (url) => {
    dispose();
    error.value = null;
    const run = generation;
    const current = () => run === generation && url === props.url;
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
        throw new Error('Не удалось показать первую страницу PDF.');
      const page = await pdf.getPage(1);
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
      const rendering = page.render({ canvas: target, viewport, background: 'white' });
      render = rendering;
      await rendering.promise;
      if (!current()) return;
      render = null;
      ready.value = true;
    } catch (cause) {
      if (!current()) return;
      dispose();
      error.value = cause instanceof Error ? cause.message : 'Не удалось показать PDF.';
    }
  },
  { immediate: true, flush: 'sync' },
);
onBeforeUnmount(dispose);
</script>
<template>
  <section
    class="attachment-pdf-preview"
    :aria-label="label"
    :aria-busy="!ready && !error"
    :data-pdf-ready="ready"
  >
    <p
      v-if="!ready && !error"
      role="status"
    >
      Показываем первую страницу PDF…
    </p>
    <button
      type="button"
      class="attachment-pdf-preview__page"
      :aria-label="`${label}. Открыть PDF`"
      @click="openFile"
    >
      <canvas
        v-show="ready"
        ref="canvas"
        role="img"
        :aria-label="label"
      />
    </button>
    <p
      v-if="error"
      role="status"
    >
      {{ error }}
      <button
        type="button"
        class="attachment-pdf-preview__fallback"
        @click="openFile"
      >
        Открыть PDF
      </button>
    </p>
  </section>
</template>
