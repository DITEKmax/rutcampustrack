import { readFileSync } from 'node:fs'
import { relative } from 'node:path'
import type { Plugin } from 'vite'
import { sitemapLastmod } from './scripts/sitemap-lastmod'
import { loadTopic } from './src/story/topic-chapters'
import { presentationHome, topicHref, topics, type Topic } from './src/topics'

const origin = 'https://ruttrack.site'
const name = 'RutCampusTrack'
const image = origin + presentationHome + 'assets/brand-20b-g08/og-titlecase-purple.png'
const homeTitle = 'За каждой отметкой целая система'
const homeLead = 'Расписание, посещаемость и учебный контекст.'
const homeDescription = 'RutCampusTrack: расписание, посещаемость и учебный контекст. Попробуй учебный день в интерактивной демонстрации.'
const criticalStylePath = new URL('./src/landing-loading.pcss', import.meta.url)

const description = (topic: Topic) => topic.lead ||
  'Как устроен интерфейс RutCampusTrack: экран учебного дня, типографика Onest и цветовые токены для светлой и тёмной темы.'

const pages = [
  { path: presentationHome, label: name, title: name + ' — ' + homeTitle, description: homeDescription },
  ...topics.map(topic => ({
    path: topicHref(topic),
    label: topic.title,
    title: topic.title + ' — ' + name,
    description: description(topic),
    topic,
  })),
]

type Page = (typeof pages)[number]

const htmlEntities: Record<string, string> = {
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}
const escapeHtml = (value: string) => value.replace(/[&<>"']/g, char => htmlEntities[char]!)
const escapeMarkdown = (value: string) => value.replace(/[\\`*_[\]<>]/g, '\\$&')
const htmlParagraph = (value: string) => '<p>' + escapeHtml(value) + '</p>'
const markdownPath = (page: Page) => page.path + 'index.md'

const illustrations: Record<Topic['slug'], string> = {
  academic: 'panels', architecture: 'panels', attendance: 'phone', campus: 'map',
  data: 'panels', design: 'panels', notifications: 'phone', reliability: 'panels',
  reports: 'pages', schedule: 'panels', security: 'shield',
}

function topicLoading(topic: Topic): string {
  return `<div class="landing-loading-topic">
  <div class="landing-loading-topic-copy">
    <div class="landing-loading-back"><span class="landing-loading-mask">← Все темы</span></div>
    <div class="landing-loading-topic-title"><span class="landing-loading-mask">${escapeHtml(topic.title)}</span></div>
    ${topic.lead ? `<div class="landing-loading-topic-lead"><span class="landing-loading-mask">${escapeHtml(topic.lead)}</span></div>` : ''}
  </div>
  <div class="landing-loading-illustration" data-shape="${illustrations[topic.slug]}">
    <span></span><span></span><span></span>
  </div>
</div>`
}

const homeLoading = `<div class="landing-loading-home">
  <div class="landing-loading-home-copy">
    <div class="landing-loading-kicker"><span class="landing-loading-mask">Учебный день становится понятнее</span></div>
    <div class="landing-loading-home-title"><span class="landing-loading-mask">За каждой отметкой<br> целая система.</span></div>
    <div class="landing-loading-home-lead"><span class="landing-loading-mask">${escapeHtml(homeLead)}</span></div>
  </div>
  <div class="landing-loading-orbit">
    <div class="landing-loading-rings"></div>
    <span class="landing-loading-orbit-first"></span>
    <span class="landing-loading-orbit-center"></span>
    <span class="landing-loading-orbit-last"></span>
  </div>
  <div class="landing-loading-stack">${'<span></span>'.repeat(8)}</div>
</div>`

const loadingLines = '<span></span><span></span><span></span>'
const loadingCode = `<div class="landing-loading-code">${loadingLines}</div>`
const loadingPhone = '<div class="landing-loading-phone"></div>'

const homeLoadingAnchors = `
<div class="landing-loading-anchor landing-loading-tiles">
  ${topics.map(topic => `<span data-tile="${topic.slug}"></span>`).join('')}
</div>
<div class="landing-loading-anchor landing-loading-demo">
  <div class="landing-loading-trace">${loadingCode.repeat(2)}</div>${loadingPhone}
</div>`

const storyLoadingAnchor = `
<div class="landing-loading-anchor landing-loading-story">
  <div class="landing-loading-lines">${loadingLines}</div>${loadingCode}
</div>`

const designLoadingAnchors = `
<div class="landing-loading-anchor landing-loading-design">
  <div class="landing-loading-lines">${loadingLines}</div>
  <div class="landing-loading-design-layout">
    ${loadingPhone}
    <div class="landing-loading-scale">${loadingLines}</div>
    <div class="landing-loading-lines">${loadingLines}</div>
  </div>
  <div class="landing-loading-palette">${'<span></span>'.repeat(5)}</div>
</div>`

function loadingShell(page: Page): string {
  const topic = 'topic' in page ? page.topic : undefined
  return `<div id="landing-loading"${topic ? ` data-loading-topic="${topic.slug}"` : ''} hidden>
  <p class="landing-loading-status" role="status">Загрузка страницы</p>
  <div aria-hidden="true">
    <div class="landing-loading-header"><span></span></div>
    ${topic ? topicLoading(topic) : homeLoading}
    ${topic ? topic.slug === 'design' ? designLoadingAnchors : storyLoadingAnchor : homeLoadingAnchors}
  </div>
</div>`
}

function metadata(page: Page): string {
  const canonical = origin + page.path
  const meta = (attribute: 'name' | 'property', key: string, value: string) =>
    `<meta ${attribute}="${key}" content="${escapeHtml(value)}" />`
  return [
    '<title>' + escapeHtml(page.title) + '</title>',
    meta('name', 'description', page.description),
    '<link rel="canonical" href="' + escapeHtml(canonical) + '" />',
    '<link rel="alternate" type="text/markdown" href="' + escapeHtml(origin + markdownPath(page)) + '" />',
    '<link rel="describedby" href="' + origin + '/llms.txt" />',
    meta('property', 'og:type', 'website'),
    meta('property', 'og:site_name', name),
    meta('property', 'og:locale', 'ru_RU'),
    meta('property', 'og:title', page.title),
    meta('property', 'og:description', page.description),
    meta('property', 'og:url', canonical),
    meta('property', 'og:image', image),
    meta('property', 'og:image:type', 'image/png'),
    meta('property', 'og:image:width', '1200'),
    meta('property', 'og:image:height', '630'),
    meta('property', 'og:image:alt', name),
    meta('name', 'twitter:card', 'summary_large_image'),
    meta('name', 'twitter:title', page.title),
    meta('name', 'twitter:description', page.description),
    meta('name', 'twitter:image', image),
    meta('name', 'twitter:image:alt', name),
  ].join('\n    ')
}

function topicNavigation(): string {
  const links = topics.map(topic =>
    '<li><a href="' + topicHref(topic) + '">' + escapeHtml(topic.title) + '</a>' +
    htmlParagraph(description(topic)) + '</li>',
  )
  return '<nav id="topics" aria-labelledby="topics-title"><h2 id="topics-title">Темы проекта</h2><ul>' +
    links.join('\n') + '</ul></nav>'
}

async function pageContent(page: Page) {
  const home = '<a href="' + presentationHome + '#topics">Все темы RutCampusTrack</a>'
  if (!('topic' in page)) {
    return {
      html: '<main class="landing-shell landing-static"><header id="top"><p>' + name +
        '</p><h1>' + homeTitle + '</h1><p id="demo">' + homeLead + '</p></header>' + topicNavigation() + '</main>',
      markdown: [
        '# ' + name,
        homeDescription,
        '## Темы проекта\n\n' + pages.slice(1).map(topic =>
          `- [${escapeMarkdown(topic.label)}](${origin + markdownPath(topic)}): ${escapeMarkdown(topic.description)}`,
        ).join('\n'),
      ],
    }
  }

  const chapters = await loadTopic(page.topic)
  const sections: string[] = []
  const markdown = ['# ' + escapeMarkdown(page.label), escapeMarkdown(page.description)]
  for (const chapter of chapters) {
    const section = chapter.title
      ? ['<h2>' + escapeHtml(chapter.title) + '</h2>', ...(chapter.lead ? [htmlParagraph(chapter.lead)] : [])]
      : []
    if (chapter.title) markdown.push('## ' + escapeMarkdown(chapter.title))
    if (chapter.lead) markdown.push(escapeMarkdown(chapter.lead))
    const heading = chapter.title ? 'h3' : 'h2'
    for (const operation of chapter.operations) {
      const alias = operation.alias ? `<span id="${escapeHtml(operation.alias)}"></span>` : ''
      section.push(`<section id="${escapeHtml(operation.id)}">${alias}<${heading}>${escapeHtml(operation.title)}</${heading}>` +
        htmlParagraph(operation.description) + '</section>')
      markdown.push((chapter.title ? '### ' : '## ') + escapeMarkdown(operation.title), escapeMarkdown(operation.description))
    }
    sections.push(chapter.title
      ? '<section id="' + escapeHtml(chapter.id) + '">' + section.join('\n') + '</section>'
      : section.join('\n'))
  }
  if (page.topic.slug === 'design') {
    for (const entry of designSections) {
      sections.push('<section id="' + entry.id + '"><h2>' + escapeHtml(entry.title) + '</h2>' + htmlParagraph(entry.lead) + '</section>')
      markdown.push('## ' + escapeMarkdown(entry.title), escapeMarkdown(entry.lead))
    }
  }
  return {
    html: '<main class="landing-shell landing-static">' + home + '<h1>' + escapeHtml(page.label) + '</h1>' +
      (page.topic.lead ? htmlParagraph(page.topic.lead) : '') + sections.join('\n') + '</main>',
    markdown,
  }
}

const designSections = [
  { id: 'design-today', title: 'Учебный день', lead: 'Текущая пара, время занятий и изменения аудитории на экране «Сегодня».' },
  { id: 'design-type', title: 'Буквы, цифры и ритм', lead: 'Onest Variable для заголовков, расписания и таблиц журнала. Табличные цифры удерживают время в колонках.' },
  { id: 'design-tokens', title: 'Цвет и переменные', lead: 'Один цветовой токен выделяет текущую пару в светлой и тёмной теме.' },
  { id: 'design-palette', title: 'Палитра проекта', lead: 'Цвета фона, текста и акцентов в светлой и тёмной теме.' },
]

function sitemap(root: string): string {
  const dates = sitemapLastmod(root)
  const entries = pages.map(({ path }) => {
    const lastmod = dates[path]
    return '  <url><loc>' + escapeHtml(origin + path) + '</loc>' +
      (lastmod ? '<lastmod>' + lastmod + '</lastmod>' : '') + '</url>'
  })
  return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${entries.join('\n')}
</urlset>
`
}

const llms = [
  '# ' + name,
  '> Публичная презентация системы учебного дня: расписание, посещаемость и учебный контекст.',
  'Презентация описывает работу приложения для студентов, старост, преподавателей и администраторов. Интерактивные сценарии используют демонстрационные данные.',
  '## Обзор\n\n- [RutCampusTrack](' + origin + presentationHome + 'index.md): Обзор презентации и список тем.',
  '## Темы проекта\n\n' + pages.slice(1).map(page =>
    `- [${escapeMarkdown(page.label)}](${origin + markdownPath(page)}): ${escapeMarkdown(page.description)}`,
  ).join('\n'),
].join('\n\n') + '\n'

const discoveryAssets = [
  { fileName: 'robots.txt', mime: 'text/plain', content: () => 'User-agent: *\nAllow: /\n\nSitemap: ' + origin + '/sitemap.xml\n' },
  { fileName: 'llms.txt', mime: 'text/plain', content: () => llms },
  ...pages.map(page => ({
    fileName: markdownPath(page).slice(presentationHome.length),
    mime: 'text/markdown',
    async content() {
      const { markdown } = await pageContent(page)
      return [...markdown, '[Презентация](' + origin + page.path + ')'].join('\n\n') + '\n'
    },
  })),
]

export function discovery(): Plugin {
  let root = ''
  let buildStyles: string
  let cachedSitemap: string | undefined
  const assets = [
    { fileName: 'sitemap.xml', mime: 'application/xml', content: () => cachedSitemap ??= sitemap(root) },
    ...discoveryAssets,
  ]
  const byFilename = new Map(pages.map(page => [page.path.slice(presentationHome.length) + 'index.html', page]))
  return {
    name: 'rct-landing-discovery',
    configResolved(config) {
      root = config.root
      if (config.command === 'build') buildStyles = readFileSync(criticalStylePath, 'utf8')
    },
    transformIndexHtml: {
      order: 'pre',
      async handler(html, context) {
        const page = byFilename.get(relative(root, context.filename).replaceAll('\\', '/'))
        if (!page) return html
        const content = await pageContent(page)
        const criticalStyles = context.server ? readFileSync(criticalStylePath, 'utf8') : buildStyles
        return html
          .replace(/(<meta charset="utf-8"\s*\/?>)/, (_match, charset) =>
            charset + '\n    <meta name="color-scheme" content="dark">\n    <style>' + criticalStyles + '</style>')
          .replace(/<title>[^<]*<\/title>/, () => metadata(page))
          .replace(/(<div id="app"[^>]*>)<\/div>/, (_match, open) =>
            loadingShell(page) + '\n' + open + '\n' + content.html + '\n</div>')
      },
    },
    async generateBundle() {
      cachedSitemap = undefined
      for (const asset of assets) {
        this.emitFile({ type: 'asset', fileName: asset.fileName, source: await asset.content() })
      }
    },
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const pathname = request.url?.split('?')[0]
        const asset = assets.find(item => pathname === presentationHome + item.fileName)
        if (!asset || (request.method !== 'HEAD' && request.method !== 'GET')) return next()
        void Promise.resolve(asset.content()).then(content => {
          response.setHeader('Content-Type', asset.mime + '; charset=utf-8')
          response.end(request.method === 'HEAD' ? undefined : content)
        }).catch(next)
      })
    },
  }
}
