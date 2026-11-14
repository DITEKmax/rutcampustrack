import { edgeKey, edges, nodes, roundedPath, type NodeId, type Point, type TopologyEdge } from './production-topology'

export const telemetryTargets = [
  'gateway', 'bff', 'auth', 'academic', 'schedule', 'attendance', 'notification', 'renderer', 'bot',
] as const satisfies NodeId[]

export const telemetryRefs = telemetryTargets.map((id, index) => ({
  id, x: 24 + 128 * index, y: 1518, width: 112, height: 42,
}))

const productPoints: Record<string, Point[]> = {
  'bot:bff': [[836, 660], [590, 660]],
  'gateway:academic': gatewayTo(306),
  'gateway:schedule': gatewayTo(528),
  'gateway:attendance': gatewayTo(742),
  'gateway:notification': gatewayTo(992),
  'bff:auth': [[392, 660], [213, 660], [213, 536]],
  'bff:academic': bffTo(359),
  'bff:schedule': bffTo(576),
  'bff:attendance': bffTo(790),
  'attendance:schedule': [[849, 798], [849, 770], [616, 770], [616, 796]],
  'attendance:academic': [[881, 798], [881, 756], [406, 756], [406, 796]],
  'academic:renderer': toData('academic', 405, 886, 1083),
  'attendance:renderer': toData('attendance', 880, 886, 1083),
  'auth:pgAcademic': [[118, 534], [118, 913], [72, 913], [72, 1003]],
  'academic:pgAcademic': toData('academic', 278, 908, 125),
  'schedule:pgSchedule': toData('schedule', 518, 929, 243),
  'gateway:redis': [[421, 534], [421, 585], [226, 585], [226, 949], [408, 949], [408, 1003]],
  'auth:redis': [[171, 534], [171, 940], [408, 940], [408, 1003]],
  'notification:redis': toData('notification', 1103, 949, 408),
  'academic:redisCache': toData('academic', 371, 918, 560),
  'academic:rabbit': toData('academic', 324, 966, 872),
  'schedule:rabbit': toData('schedule', 593, 966, 872),
  'attendance:rabbit': toData('attendance', 830, 966, 872),
  'rabbit:notification': [[942, 1005], [942, 897], [1038, 897], [1038, 852]],
  'rabbit:bot': [[942, 1005], [942, 897], [919, 897], [919, 688]],
}

function gatewayTo(x: number): Point[] {
  return [[551, 534], [551, 575], [630, 575], [630, 712], [x, 712], [x, 796]]
}

function bffTo(x: number): Point[] {
  return [[517, 686], [517, 739], [x, 739], [x, 796]]
}

function toData(from: NodeId, x1: number, lane: number, x2: number): Point[] {
  return [[x1, nodes[from].y + nodes[from].height], [x1, lane], [x2, lane], [x2, 1003]]
}

function routePoints(link: TopologyEdge): Point[] {
  const { from, to } = link
  if (from === 'prometheus') {
    const target = telemetryRefs.find(({ id }) => id === to)!
    const x = target.x + target.width / 2
    return [[330, 1334], [330, 1490], [x, 1490], [x, target.y - 2]]
  }
  if (to === 'tempo' && from !== 'grafana') {
    const target = telemetryRefs.find(({ id }) => id === from)!
    const x = target.x + target.width / 2
    return [[x, target.y + target.height], [x, 1611], [784, 1611], [784, 1308], [752, 1308]]
  }
  const points = productPoints[from + ':' + to] ?? link.points
  if (!points) throw new Error('Missing topology route ' + edgeKey(link))
  return points
}

function routeGroup({ from, to }: TopologyEdge) {
  if (from === 'prometheus') return 'metrics'
  if (to === 'tempo' && from !== 'grafana') return 'traces'
  if (from === 'gateway' && ['academic', 'schedule', 'attendance', 'notification'].includes(to)) return 'gateway-domains'
  if (from === 'bff' && ['academic', 'schedule', 'attendance'].includes(to)) return 'bff-domains'
  if (from === 'rabbit') return 'rabbit-subscribers'
  if (['rabbit', 'renderer', 'redis'].includes(to)) return 'to-' + to
}

type Segment = { axis: number; min: number; max: number; route: Route }
interface Route {
  link: TopologyEdge
  key: string
  group?: string | undefined
  path: string
  gaps: Point[]
}

const horizontal: Segment[] = []
const vertical: Segment[] = []

export const overviewRoutes = edges.map((link): Route => {
  const points = routePoints(link)
  const route = { link, key: edgeKey(link), group: routeGroup(link), path: roundedPath(points), gaps: [] }
  points.forEach(([x, y], index) => {
    const previous = points[index - 1]
    if (!previous) return
    const [px, py] = previous
    if (x === px && y !== py) vertical.push({ axis: x, min: Math.min(y, py), max: Math.max(y, py), route })
    else if (y === py && x !== px) horizontal.push({ axis: y, min: Math.min(x, px), max: Math.max(x, px), route })
  })
  return route
})

const junctions = new Map<string, { point: Point; group: string; links: Set<string> }>()
// A common route joins at a branch. Other crossings keep the vertical path and interrupt the horizontal one.
for (const h of horizontal) {
  for (const v of vertical) {
    if (v.axis <= h.min + 4 || v.axis >= h.max - 4) continue
    const point = [v.axis, h.axis] as const
    const sameGroup = h.route.group && h.route.group === v.route.group
    if (sameGroup && h.route !== v.route && (h.axis === v.min || h.axis === v.max)) {
      const group = h.route.group!
      const key = group + ':' + point
      const junction = junctions.get(key) ?? { point, group, links: new Set<string>() }
      junction.links.add(h.route.key).add(v.route.key)
      junctions.set(key, junction)
    } else if (!sameGroup && h.route !== v.route && h.axis > v.min + 4 && h.axis < v.max - 4
      && !h.route.gaps.some(([x, y]) => x === v.axis && y === h.axis)) {
      h.route.gaps.push(point)
    }
  }
}
export const overviewJunctions = [...junctions.values()]

export const mobileRoutes = Object.fromEntries(Object.keys(nodes).map((from) => [
  from,
  edges.filter((edge) => edge.from === from && edge.from !== 'prometheus'
    && !(edge.to === 'tempo' && edge.from !== 'grafana')),
])) as Record<NodeId, TopologyEdge[]>
