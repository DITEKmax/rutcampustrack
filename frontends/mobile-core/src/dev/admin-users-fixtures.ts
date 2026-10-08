import { AdminAccessClient, type AdminAccessCatalog, type AdminAccessProfile } from '../features/admin-access/admin-access-client'
import { AdminUsersClient, type AdminManagedRole, type AdminUser } from '../features/admin-users/admin-users-client'

export const adminUsersReviewStates = [
  ['permissions-create','Создать · права доступа'], ['permissions-dependencies','Права · зависимости'], ['permissions-full','Права · полный доступ'], ['permissions-confirm','Права · подтверждение выдачи'], ['permissions-mixed-revoke','Права · шаг отзыва'], ['permissions-mixed-grant','Права · шаг выдачи после отзыва'], ['permissions-mixed-saved','Права · смешанное изменение сохранено'], ['permissions-conflict','Права · конфликт версии'], ['permissions-uncertain','Права · результат неизвестен'], ['permissions-revoke','Права · отзыв ADMIN'], ['permissions-revoke-confirm','Отзыв ADMIN · подтверждение'], ['permissions-saved','Права сохранены'], ['limited-credentials','Ограниченный ADMIN · восстановление доступа'], ['created-no-credentials','Создано без права просмотра пароля'], ['limited-readonly','Ограниченный ADMIN · просмотр'], ['limited-manage','Ограниченный ADMIN · правка'], ['limited-target-admin','Ограниченный ADMIN · защищённая цель'], ['limited-no-access','Нет права просмотра'], ['list','Пользователи · список'], ['filters','Фильтры и сортировка'], ['filters-role','Фильтр ролей раскрыт'], ['filters-status','Статусы выбранной роли'], ['filters-sort','Сортировка ФИО раскрыта'], ['filters-student-statuses','Студент · все статусы'], ['filters-teacher-statuses','Преподаватель · все статусы'], ['filters-admin-statuses','Администратор · все статусы'], ['filters-headman-statuses','Староста · все статусы'], ['list-multirole','Пользователи · несколько ролей'],
  ['detail','Карточка преподавателя'], ['detail-password-changed','Карточка · пароль изменён'], ['detail-student','Карточка студента'], ['detail-multirole','Карточка · несколько ролей'], ['detail-student-graduated','Студент · выпускник'], ['detail-teacher-dismissed','Преподаватель · уволен'], ['detail-headman-suspended','Староста · роль приостановлена'], ['detail-admin-archived','Администратор · роль в архиве'], ['create-student','Создать студента'], ['create-teacher','Создать преподавателя'], ['create-admin','Создать администратора · права'], ['create-role','Выбор роли'], ['create-group','Выбор группы'], ['created','Создано после подтверждения'], ['edit','Редактировать данные'], ['role-editor','Статус роли'], ['add-role','Добавить роль'], ['transfer','Перевод студента'], ['recovery','Восстановление доступа'], ['recovery-issued','Ссылка восстановления · отдельная задача'], ['recovery-done','Восстановление · Готово очищает ссылку'], ['recovery-back','Восстановление · Назад очищает ссылку'], ['recovery-new-issue','Восстановление · повторное открытие и новая выдача'], ['recovery-after-unknown','Восстановление · новая выдача после неизвестного ответа'], ['recovery-expired','Восстановление · срок ссылки истёк'], ['recovery-uncertain','Восстановление · результат неизвестен'], ['archive-confirm','Архивировать аккаунт'], ['archive-password','Архив · подтверждение паролем'], ['archive-password-error','Архив · неверный пароль'], ['archive-password-corrected','Архив · исправленный пароль и подтверждение'], ['archive-expired','Архив · предпросмотр устарел'], ['archive-conflict','Архив · данные изменились'], ['archive-uncertain','Архив · повтор подтверждения'], ['restore-confirm','Восстановить без прав'], ['restore-result','Аккаунт восстановлен'], ['empty','Пользователи не найдены'], ['error','Ошибка загрузки'], ['validation','Проверка заполнения'], ['create-uncertain','Создание · результат неизвестен'], ['profile-error','Ошибка сохранения данных'],
].map(([state,label]) => ({ id: `users-${state}`, state: state!, label: label!, group: 'Пользователи', entry: 'admin-users-review.html', gate: '.admin-users-screen' }))

function grant(role: AdminManagedRole, groupId: number | null = null, status = 'ACTIVE') {
  return { role,status,groupId,groupName: groupId ? 'ИКБО-01-24' : null,selectable:status === 'ACTIVE',readOnly:false,canUpdate:role !== 'ADMIN' && role !== 'HEADMAN',applicableStatuses:role === 'STUDENT' ? ['ACTIVE','EXPELLED','GRADUATED','SUSPENDED','ARCHIVED'] : role === 'TEACHER' ? ['ACTIVE','DISMISSED','SUSPENDED','ARCHIVED'] : role === 'HEADMAN' ? ['ACTIVE','SUSPENDED','ARCHIVED'] : ['ACTIVE','ARCHIVED'],blockedReason:null }
}
function fixtureUser(id: number, firstName: string,lastName: string,role: 'STUDENT'|'TEACHER'|'ADMIN', groupId: number | null = null): AdminUser {
  return { id,firstName,lastName,middleName:null,fullName:`${firstName} ${lastName}`,login:`user.0${id}`,initialPassword:'demo-A8mQ-42ks',role,status:'ACTIVE',groupId,employeeNumber:role === 'TEACHER' ? 'Т-00421' : null,telegramId:123456789,createdAt:'2026-09-01T10:00:00Z',roles:[grant(role,groupId)] as AdminUser['roles'] }
}
export function createAdminUsersReviewApis(state: string): {client:AdminUsersClient;accessClient:AdminAccessClient;authority:AdminAccessProfile;observations:{recoveryPosts:number;archivePosts:number;archiveOperationStable:boolean;archiveProofUpdated:boolean;accessMutations:number}} {
  const entries = [
    ['DASHBOARD_VIEW','Просмотр сводки','overview',[]], ['USERS_VIEW','Просмотр пользователей','users',[]], ['USERS_MANAGE','Управление пользователями','users',['USERS_VIEW']], ['USER_CREDENTIALS_MANAGE','Начальные пароли и восстановление доступа','users',['USERS_VIEW']], ['USERS_ARCHIVE','Архивирование и восстановление пользователей','users',['USERS_VIEW']], ['GROUPS_VIEW','Просмотр групп','groups',[]], ['GROUPS_MANAGE','Управление группами','groups',['GROUPS_VIEW']], ['SEMESTERS_VIEW','Просмотр семестров','semesters',[]], ['SEMESTERS_MANAGE','Управление семестрами','semesters',['SEMESTERS_VIEW']], ['CAMPUS_MAP_MANAGE','Управление картой','map',[]], ['DATA_DELETE','Окончательное удаление данных','dangerous',[]], ['ADMIN_ACCESS_MANAGE','Управление администраторами и их правами','administration',[]],
  ] as const
  const descriptions: Record<(typeof entries)[number][0], string> = {
    DASHBOARD_VIEW: 'Сводная статистика',
    USERS_VIEW: 'Карточки и списки без начальных паролей',
    USERS_MANAGE: 'Создание и изменение обычных пользователей',
    USER_CREDENTIALS_MANAGE: 'Начальные пароли и ссылки восстановления обычных пользователей',
    USERS_ARCHIVE: 'Защищённое архивирование и восстановление обычных пользователей',
    GROUPS_VIEW: 'Реестр и карточки групп',
    GROUPS_MANAGE: 'Создание, изменение, старосты, продвижение и архив групп',
    SEMESTERS_VIEW: 'Список и карточки семестров',
    SEMESTERS_MANAGE: 'Создание, изменение, активация и архив семестров',
    CAMPUS_MAP_MANAGE: 'Здания, этажи и планы кампуса',
    DATA_DELETE: 'Дополнительно требует управление областью и защищённое подтверждение',
    ADMIN_ACCESS_MANAGE: 'Доступно только полному администратору',
  }
  const catalog: AdminAccessCatalog = {permissions:entries.map(([code,label,group,requires]) => ({code,label,group,requires:[...requires],fullOnly:code === 'ADMIN_ACCESS_MANAGE',description:descriptions[code]}))}
  const limited = state.startsWith('limited') || state === 'created-no-credentials'
  const authority: AdminAccessProfile = {userId:99,assigned:true,mode:limited ? 'LIMITED' : 'FULL',permissions:limited ? state === 'limited-no-access' ? ['DASHBOARD_VIEW'] : state === 'limited-manage' || state === 'limited-target-admin' || state === 'created-no-credentials' ? ['USERS_VIEW','USERS_MANAGE'] : state === 'limited-credentials' ? ['USERS_VIEW','USER_CREDENTIALS_MANAGE'] : ['USERS_VIEW'] : entries.map(([code]) => code),version:5}
  let targetAccess: AdminAccessProfile = {userId:3,assigned:state.startsWith('permissions') && state !== 'permissions-create' || state === 'limited-target-admin',mode:state.startsWith('permissions') && state !== 'permissions-create' || state === 'limited-target-admin' ? 'LIMITED' : null,permissions:state.startsWith('permissions') && state !== 'permissions-create' || state === 'limited-target-admin' ? ['USERS_VIEW'] : [],version:state.startsWith('permissions') ? 4 : 0}
  if(state.startsWith('permissions-mixed')) targetAccess={...targetAccess,permissions:['USERS_VIEW','GROUPS_VIEW']}
  let users: AdminUser[] = [fixtureUser(1,'Ирина','Соколова','STUDENT',7),fixtureUser(2,'Алексей','Морозов','STUDENT',8),fixtureUser(3,'Мария','Волкова','TEACHER')]
  if (targetAccess.assigned) users[2] = {...users[2]!,roles:[...users[2]!.roles,grant('ADMIN')] as AdminUser['roles']}
  users[1] = { ...users[1]!, roles:[grant('STUDENT',8),{...grant('HEADMAN',8,'SUSPENDED'),groupName:'ИКБО-02-24'}] as AdminUser['roles'] }
  if(state === 'list-multirole' || state === 'detail-multirole') users[2]={...users[2]!,roles:[grant('TEACHER'),grant('ADMIN') ] as AdminUser['roles']}
  if(state === 'detail-student-graduated') users[0]={...users[0]!,roles:[grant('STUDENT',7,'GRADUATED')] as AdminUser['roles']}
  if(state === 'detail-teacher-dismissed') users[2]={...users[2]!,roles:[grant('TEACHER',null,'DISMISSED')] as AdminUser['roles']}
  if(state === 'detail-headman-suspended') users[0]={...users[0]!,roles:[grant('STUDENT',7),grant('HEADMAN',7,'SUSPENDED')] as AdminUser['roles']}
  if(state === 'detail-admin-archived') users[2]={...users[2]!,roles:[grant('TEACHER'),grant('ADMIN',null,'ARCHIVED')] as AdminUser['roles']}
  if (state.startsWith('restore')) users[2] = { ...users[2]!,status:'ARCHIVED',roles:[grant('TEACHER',null,'ARCHIVED')] as AdminUser['roles'] }
  if (state === 'detail-password-changed') users[2] = { ...users[2]!, initialPassword:null }
  const json = (value: unknown,status = 200): Response => new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json'}})
  const problem = (detail: string,status = 409): Response => json({detail,title:'Не удалось сохранить действие',status},status)
  const observations = {recoveryPosts:0,archivePosts:0,archiveOperationStable:true,archiveProofUpdated:false,accessMutations:0}
  let firstArchive: {operationId:string;previewDigest:string;password?:string} | null = null
  const fetcher: typeof fetch = async (input,init) => {
    if (init?.signal?.aborted) throw new DOMException('Aborted','AbortError')
    const url = new URL(String(input),'https://review.invalid')
    const method = init?.method ?? 'GET'
    if (url.pathname === '/api/academic/admin-access/catalog') return json(catalog)
    if (url.pathname === '/api/academic/admin-access/me') return json(authority)
    if (/^\/api\/academic\/users\/\d+\/admin-access$/.test(url.pathname)) {
      if (method === 'GET') return json(targetAccess)
      observations.accessMutations++
      if (state === 'permissions-conflict') { targetAccess = {...targetAccess,permissions:['USERS_VIEW','USERS_MANAGE'],version:targetAccess.version+1}; return json({status:409,code:'ADMIN_ACCESS_CONFLICT',currentProfile:targetAccess,detail:'Права изменились в другом сеансе.'},409) }
      if (state === 'permissions-uncertain') throw new TypeError('Synthetic permissions response lost')
      if (method === 'DELETE') { targetAccess = {...targetAccess,assigned:false,mode:null,permissions:[],version:targetAccess.version+1}; users = users.map(user => user.id === 3 ? {...user,roles:user.roles.map(grant => grant.role === 'ADMIN' ? {...grant,status:'ARCHIVED'} : grant)} as AdminUser : user); return new Response(null,{status:204}) }
      const payload = JSON.parse(String(init?.body)) as {mode:'FULL'|'LIMITED';permissions:string[];expectedVersion:number}
      targetAccess = {...targetAccess,assigned:true,mode:payload.mode,permissions:payload.mode === 'FULL' ? entries.map(([code]) => code) : payload.permissions,version:targetAccess.version+1}
      users = users.map(user => user.id === 3 ? {...user,roles:[...user.roles.filter(grant => grant.role !== 'ADMIN'),grant('ADMIN')]} as AdminUser : user)
      return json(targetAccess)
    }
    if (url.pathname === '/api/academic/groups/options') {
      const groups = [{id:7,name:'ИКБО-01-24',alphabeticCode:'ИКБО',numericCode:'124',currentCourse:1,trainingDurationYears:4,durationStatus:'KNOWN',status:'ACTIVE',draftReason:null,studentCount:24,headmanFio:'Ирина Соколова',createdAt:'2026-09-01T00:00:00Z'}, {id:8,name:'ИКБО-02-24',alphabeticCode:'ИКБО',numericCode:'224',currentCourse:2,trainingDurationYears:4,durationStatus:'KNOWN',status:'ACTIVE',draftReason:null,studentCount:22,headmanFio:'Алексей Морозов',createdAt:'2026-09-01T00:00:00Z'}]
      const found = groups.filter(group => !url.searchParams.get('search') || group.name.toLocaleLowerCase('ru-RU').includes(url.searchParams.get('search')!.toLocaleLowerCase('ru-RU'))).map(({id,name}) => ({id,name}))
      return json({items:found,number:0,size:20,totalElements:found.length,totalPages:found.length ? 1 : 0})
    }
    if (url.pathname === '/api/academic/users' && method === 'GET') {
      if (state === 'error') return problem('Не удалось загрузить пользователей. Попробуй ещё раз.',503)
      const query = url.searchParams.get('search')?.toLocaleLowerCase('ru-RU')
      const role = url.searchParams.get('role')
      const roleStatus = url.searchParams.get('roleStatus')
      const found = state === 'empty' ? [] : users.filter(user => (!query || `${user.fullName} ${user.login}`.toLocaleLowerCase('ru-RU').includes(query)) && (!role || user.roles.some(grant => grant.role === role && (!roleStatus || grant.status === roleStatus))))
      const sorted = [...found].sort((a,b) => a.lastName.localeCompare(b.lastName,'ru'))
      if (url.searchParams.getAll('sort').some(sort => sort.endsWith(',desc'))) sorted.reverse()
      const number = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 3)
      return json({_embedded:{users:sorted.map(user => ({...user,initialPassword:undefined}))},page:{number,size,totalElements:state === 'list' ? 1248 : sorted.length,totalPages:state === 'list' ? 416 : sorted.length ? 1 : 0}})
    }
    if (url.pathname === '/api/academic/users' && method === 'POST') {
      if (state === 'create-uncertain') throw new TypeError('Synthetic disconnected response')
      const payload = JSON.parse(String(init?.body)) as Record<string,unknown>
      const created = {...fixtureUser(4,String(payload.firstName),String(payload.lastName),payload.role as 'STUDENT'|'TEACHER'|'ADMIN',payload.groupId ? Number(payload.groupId) : null),middleName:payload.middleName ? String(payload.middleName) : null,employeeNumber:payload.employeeNumber ? String(payload.employeeNumber) : null}
      users.push(created)
      return json(created,201)
    }
    const match = url.pathname.match(/^\/api\/academic\/users\/(\d+)(.*)$/)
    if (match) {
      const id = Number(match[1]),tail = match[2]
      const user = users.find(item => item.id === id)
      if (!user) return problem('Пользователь не найден.',404)
      if (tail === '/archive-preview') return json({userId:id,linkedGroupCount:2,attendanceMarksCount:128,activeHeadmanGroupCount:1,soleTeacherAssignmentCount:2,requiresPassword:state.startsWith('archive-password'),previewDigest:'a'.repeat(64),expiresAt:new Date(Date.now() + (state === 'archive-expired' ? -1000 : 600000)).toISOString()})
      if (tail === '/archive' || tail === '/restore') {
        if (tail === '/archive') {
          observations.archivePosts++
          const payload = JSON.parse(String(init?.body)) as {operationId:string;previewDigest:string;password?:string}
          if (firstArchive) {
            observations.archiveOperationStable = observations.archiveOperationStable && payload.operationId === firstArchive.operationId && payload.previewDigest === firstArchive.previewDigest
            observations.archiveProofUpdated = payload.password !== firstArchive.password
          } else firstArchive = payload
          if (state === 'archive-password-error' || state === 'archive-password-corrected' && observations.archivePosts === 1) return json({status:403,code:'CURRENT_PASSWORD_INVALID',detail:'Текущий пароль неверен.'},403)
        }
        if (state === 'archive-conflict') return problem('archive_preview_stale')
        if (state === 'archive-uncertain') throw new TypeError('Synthetic archive response lost')
        const restored = tail === '/restore'
        users = users.map(item => item.id === id ? {...item,status:restored ? 'ACTIVE' : 'ARCHIVED',roles:item.roles.map(grant => ({...grant,status:'ARCHIVED',selectable:false,canUpdate:restored,applicableStatuses:restored ? ['ACTIVE'] : grant.applicableStatuses})) as AdminUser['roles']} : item)
        return new Response(null,{status:204})
      }
      if (tail === '' && method === 'GET') return json(user)
      if (tail === '' && method === 'PATCH') {
        if (state === 'profile-error') return problem('Табельный номер уже используется другим пользователем.')
        const payload = JSON.parse(String(init?.body)) as Partial<AdminUser>
        const updated = {...user,...payload,fullName:[payload.firstName ?? user.firstName,payload.lastName ?? user.lastName,payload.middleName ?? user.middleName].filter(Boolean).join(' '),initialPassword:null}
        users = users.map(item => item.id === id ? updated : item)
        return json(updated)
      }
      if (tail?.startsWith('/roles/') && method === 'PUT') {
        const role = tail.split('/').at(-1) as AdminManagedRole
        const payload = JSON.parse(String(init?.body)) as {status:string;groupId?:number}
        const updated = {...user,roles:[...user.roles.filter(item => item.role !== role),grant(role,payload.groupId ?? user.groupId,payload.status)] as AdminUser['roles']}
        users = users.map(item => item.id === id ? updated : item)
        return json(updated)
      }
      if (tail === '/transfer') {
        const payload = JSON.parse(String(init?.body)) as {newGroupId:number}
        const updated = {...user,groupId:payload.newGroupId,roles:user.roles.map(grant => grant.role === 'STUDENT' ? {...grant,groupId:payload.newGroupId,groupName:'ИКБО-02-24'} : grant)}
        users = users.map(item => item.id === id ? updated : item)
        return json(updated)
      }
    }
    if (/^\/api\/auth\/admin\/users\/\d+\/password-reset-link$/.test(url.pathname)) {
      observations.recoveryPosts++
      if ((state === 'recovery-uncertain' || state === 'recovery-after-unknown') && observations.recoveryPosts === 1) throw new TypeError('Synthetic recovery response lost')
      return json({url:`https://review.invalid/password-reset#synthetic-demo-ticket-${observations.recoveryPosts}`,expiresAt:new Date(Date.now()+(state === 'recovery-expired' ? -1000 : 3600000)).toISOString(),expiresInSeconds:3600},201)
    }
    return problem('Состояние проверки недоступно.',404)
  }
  const options = {accessToken:() => 'synthetic-admin-review',fetcher}
  return {client:new AdminUsersClient(options),accessClient:new AdminAccessClient(options),authority,observations}
}
