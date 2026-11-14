import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { topicChapterContentPaths } from '../src/story/topic-chapters.ts'
import { presentationHome, topicHref, topics, type Topic } from '../src/topics.ts'

const landingRoot = fileURLToPath(new URL('../', import.meta.url))

const homeContent = [
  'index.html',
  'src/LandingApp.vue',
  'src/LandingHero.vue',
  'src/LandingDeviceShowcase.vue',
  'src/LandingMetrics.vue',
  'src/TopicTiles.vue',
  'src/topics.ts',
  'src/art/surface-return',
  'src/art/topics-pwa',
  'src/demo/LandingDemo.vue',
  'src/story/ProductionOverview.vue',
  'src/story/production-topology.ts',
  'src/story/production-topology-layout.ts',
  'src/story/TopologySelection.vue',
  'src/story/DescentScene.vue',
]

const topicContent: Partial<Record<Topic['slug'], readonly string[]>> = {
  architecture: ['src/story/ArchitectureRequestFlow.vue'],
  campus: ['src/campus', 'public/campus-floor-plans'],
  design: ['src/design'],
  reports: ['src/reports', 'public/report-examples'],
}

function git(cwd: string, ...args: string[]): string | undefined {
  try {
    return execFileSync('git', ['--literal-pathspecs', ...args], {
      cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'],
    }).trim()
  } catch {
    return undefined
  }
}

export function collectSitemapLastmod(root = landingRoot): Record<string, string> {
  // Git treats a shallow boundary as a root commit, giving unchanged files a new date.
  if (git(root, 'rev-parse', '--is-shallow-repository') !== 'false') return {}

  const dates: Record<string, string> = {}
  function record(route: string, paths: readonly string[]) {
    const date = git(root, 'log', '-1', '--format=%cI', 'HEAD', '--', ...paths)
    if (date && Number.isFinite(Date.parse(date))) {
      dates[route] = new Date(date).toISOString().slice(0, 10)
    }
  }

  record(presentationHome, homeContent)
  for (const topic of topics) {
    record(topicHref(topic), [
      topic.slug + '/index.html',
      'src/art/topics-pwa/' + topic.slug + '.svg',
      ...topicChapterContentPaths(topic),
      ...(topicContent[topic.slug] ?? []),
    ])
  }
  return dates
}

export function sitemapLastmod(root: string): Record<string, string> {
  const encoded = process.env.LANDING_SITEMAP_LASTMOD
  if (!encoded) return collectSitemapLastmod(root)

  const dates = JSON.parse(Buffer.from(encoded, 'base64url').toString('utf8')) as Record<string, unknown>
  const validDates: Record<string, string> = Object.create(null)
  for (const [route, date] of Object.entries(dates)) {
    if (typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date) &&
      Number.isFinite(Date.parse(date)) && new Date(date).toISOString().slice(0, 10) === date) {
      validDates[route] = date
    }
  }
  return validDates
}

if (import.meta.main) {
  const json = JSON.stringify(collectSitemapLastmod())
  console.log(process.argv.includes('--build-arg') ? Buffer.from(json).toString('base64url') : json)
}
