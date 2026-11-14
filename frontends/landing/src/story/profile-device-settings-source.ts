import type { StoryOperation } from './story-source'

export const profileDeviceSettingsOperations: StoryOperation[] = [
  {
    "id": "profile-device-settings-profile",
    "title": "Профиль, роль и аватар",
    "description": "В профиле видны активная роль и группа, если они есть. «Сменить роль» открывает отдельное действие, а «Безопасность», «Активные сеансы» и «История аккаунта» ведут к настройкам аккаунта. Веб-клиент читает собственный профиль. Для аватара он сохраняет ID готового пресета, а затем перечитывает профиль, чтобы подтвердить изменение. Пустой ID сбрасывает выбор.",
    "frames": [
      {
        "title": "Профиль и отдельные действия аккаунта",
        "path": "frontends/mobile-core/src/features/profile/ProfileScreen.vue",
        "language": "typescript",
        "lines": [
          [
            70,
            "const rows = ["
          ],
          [
            71,
            "  { route: 'role-switch', label: 'Сменить роль', icon: profileRoleSwitch },"
          ],
          [
            72,
            "  { route: 'appearance', label: 'Оформление', icon: profileAppearance },"
          ],
          [
            73,
            "  { route: 'security', label: 'Безопасность', icon: profileSecurity },"
          ],
          [
            74,
            "  { route: 'sessions', label: 'Активные сеансы', icon: profileSessions },"
          ],
          [
            75,
            "  { route: 'history', label: 'История аккаунта', icon: profileHistory },"
          ],
          [
            76,
            "] as const satisfies readonly {"
          ],
          [
            77,
            "  route: Extract<ProfileRoute, 'role-switch' | 'appearance' | 'security' | 'sessions' | 'history'>;"
          ],
          [
            78,
            "  label: string;"
          ],
          [
            79,
            "  icon: string;"
          ],
          [
            172,
            "          <div class=\"profile-card__copy\">"
          ],
          [
            173,
            "            <p class=\"profile-card__name\">"
          ],
          [
            174,
            "              {{ snapshot.displayName }}"
          ],
          [
            175,
            "            </p>"
          ],
          [
            176,
            "            <p"
          ],
          [
            177,
            "              v-if=\"showActiveRole || snapshot.groupLabel\""
          ],
          [
            178,
            "              class=\"profile-card__meta\""
          ],
          [
            179,
            "            >"
          ],
          [
            180,
            "              <template v-if=\"showActiveRole\">"
          ],
          [
            181,
            "                {{"
          ],
          [
            182,
            "                  snapshot.activeRole ? roleLabel(snapshot.activeRole) : 'Роль не выбрана'"
          ],
          [
            183,
            "                }} </template"
          ],
          [
            184,
            "              ><span v-if=\"snapshot.groupLabel\""
          ],
          [
            185,
            "                >{{ showActiveRole ? ' · ' : '' }}{{ snapshot.groupLabel }}</span"
          ],
          [
            186,
            "              >"
          ],
          [
            187,
            "            </p>"
          ]
        ]
      },
      {
        "title": "Чтение собственных данных без кеша",
        "path": "frontends/web-vue/src/features/profile/own-profile.ts",
        "language": "typescript",
        "lines": [
          [
            33,
            "    const promise = (async () => {"
          ],
          [
            34,
            "      try {"
          ],
          [
            35,
            "        const response = await transport.fetcher('/api/academic/users/me', { cache: 'no-store' })"
          ],
          [
            36,
            "        if (!response.ok) throw new Error('Не удалось загрузить профиль')"
          ],
          [
            37,
            "        const value: unknown = await response.json()"
          ],
          [
            38,
            "        transport.assertCurrent()"
          ],
          [
            39,
            "        ownProfileTelegramId(value, userId)"
          ],
          [
            40,
            "        const profile = value as OwnProfile"
          ],
          [
            41,
            "        if ("
          ],
          [
            42,
            "          [profile.firstName, profile.lastName].some((value) => typeof value !== 'string') ||"
          ],
          [
            43,
            "          (profile.avatarId != null && !/^avatar_[0-9]{2}$/.test(profile.avatarId))"
          ],
          [
            44,
            "        ) throw new Error('Некорректные данные профиля')"
          ],
          [
            45,
            "        if (active && ticket === revision) {"
          ],
          [
            46,
            "          own.value = {"
          ],
          [
            47,
            "            id: profile.id, firstName: profile.firstName, lastName: profile.lastName,"
          ],
          [
            48,
            "            middleName: profile.middleName, telegramId: profile.telegramId, avatarId: profile.avatarId,"
          ],
          [
            49,
            "          }"
          ]
        ]
      },
      {
        "title": "Контракт собственного профиля и пресета",
        "path": "services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/UserApi.java",
        "language": "java",
        "lines": [
          [
            907,
            "    @GetMapping(\"/me\")"
          ],
          [
            908,
            "    ResponseEntity<EntityModel<UserResponse>> getMe();"
          ],
          [
            909,
            ""
          ],
          [
            910,
            "    @Operation("
          ],
          [
            911,
            "            summary = \"Сменить аватар текущего пользователя (BUG-004)\","
          ],
          [
            912,
            "            description ="
          ],
          [
            913,
            "                    \"Сохраняет идентификатор пресета. Передайте пустой avatarId чтобы сбросить.\")"
          ],
          [
            914,
            "    @ApiResponses({"
          ],
          [
            915,
            "        @ApiResponse(responseCode = \"200\", description = \"Аватар обновлён\"),"
          ],
          [
            916,
            "        @ApiResponse(responseCode = \"400\", description = \"Неверный avatarId\"),"
          ],
          [
            917,
            "        @ApiResponse(responseCode = \"403\", description = \"Нет прав доступа\")"
          ],
          [
            918,
            "    })"
          ],
          [
            919,
            "    @PatchMapping(\"/me/avatar\")"
          ],
          [
            920,
            "    ResponseEntity<EntityModel<UserResponse>> updateMyAvatar("
          ],
          [
            921,
            "            @Valid @RequestBody UpdateAvatarRequest request);"
          ]
        ]
      },
      {
        "title": "Клиент сохраняет пресет и перечитывает профиль",
        "path": "frontends/web-vue/src/features/profile/use-profile.ts",
        "language": "typescript",
        "lines": [
          [
            149,
            "  async function saveAvatar(avatarId: string) {"
          ],
          [
            150,
            "    if (avatarBusy.value || uncertain.value) return"
          ],
          [
            151,
            "    avatarBusy.value = true"
          ],
          [
            152,
            "    ownError.value = ''"
          ],
          [
            153,
            "    ownProfile.invalidateReads()"
          ],
          [
            154,
            "    try {"
          ],
          [
            155,
            "      const response = await transport.fetcher('/api/academic/users/me/avatar', {"
          ],
          [
            156,
            "        method: 'PATCH',"
          ],
          [
            157,
            "        headers: { 'Content-Type': 'application/json' },"
          ],
          [
            158,
            "        body: JSON.stringify({ avatarId }),"
          ],
          [
            159,
            "      })"
          ],
          [
            160,
            "      if (!response.ok) {"
          ],
          [
            161,
            "        ownError.value ="
          ],
          [
            162,
            "          response.status === 403"
          ],
          [
            163,
            "            ? 'Изменение аватара недоступно для текущего контекста.'"
          ],
          [
            164,
            "            : 'Аватар не сохранён. Проверь данные и повтори действие.'"
          ],
          [
            165,
            "        return"
          ],
          [
            166,
            "      }"
          ],
          [
            167,
            "      await ownProfile.load(true)"
          ],
          [
            168,
            "      if (own.value?.avatarId === (avatarId || null) || (!own.value?.avatarId && !avatarId))"
          ],
          [
            169,
            "        showSuccess('Аватар сохранён.')"
          ],
          [
            170,
            "      else ownError.value = 'Не удалось подтвердить выбранный аватар. Обнови профиль.'"
          ]
        ]
      }
    ]
  },
  {
    "id": "profile-device-settings-theme",
    "title": "Тема и ревизия устройства",
    "description": "Выбор темы применяется локально. После входа клиент синхронизирует его с Auth, где запись привязана к cookie этого браузерного контекста. Если на телефоне включить тёмную тему, независимый профиль браузера на ноутбуке сохраняет свой выбор. PUT отправляет ожидаемую ревизию. Сервер сравнивает её с текущей под блокировкой и при успехе увеличивает на один. После конфликта или неизвестного результата клиент сначала перечитывает запись.",
    "frames": [
      {
        "title": "Локальный выбор и повторное чтение перед записью",
        "path": "frontends/shared/foundation/device-theme.ts",
        "language": "typescript",
        "lines": [
          [
            81,
            "  const stopTheme = options.theme.subscribe((snapshot, cause) => {"
          ],
          [
            82,
            "    const systemOnly ="
          ],
          [
            83,
            "      snapshot.mode === previous.mode && snapshot.resolvedMode !== previous.resolvedMode"
          ],
          [
            84,
            "    previous = snapshot"
          ],
          [
            85,
            "    if (applying || cause === 'policy' || systemOnly || disposed) return"
          ],
          [
            86,
            "    choice = snapshot.mode"
          ],
          [
            87,
            "    options.preference.write(choice)"
          ],
          [
            88,
            "    epoch++"
          ],
          [
            89,
            "    schedule()"
          ],
          [
            90,
            "  })"
          ],
          [
            129,
            "          if (observed.theme === choice) break"
          ],
          [
            130,
            "          const selected = choice,"
          ],
          [
            131,
            "            selectedEpoch = epoch"
          ],
          [
            132,
            "          try {"
          ],
          [
            133,
            "            const saved = await options.transport.write(activeSession, selected, observed.revision)"
          ],
          [
            134,
            "            if (!current(activeGeneration)) break"
          ],
          [
            135,
            "            observed = saved"
          ],
          [
            136,
            "            if (selectedEpoch !== epoch) continue"
          ],
          [
            137,
            "            break"
          ],
          [
            138,
            "          } catch (error) {"
          ],
          [
            139,
            "            if (!current(activeGeneration)) break"
          ],
          [
            140,
            "            if (error instanceof DeviceThemeHttpError && error.status !== 409 && error.status < 500)"
          ],
          [
            141,
            "              break"
          ],
          [
            142,
            "            // An interrupted PUT may have committed: read first, never retry its old revision blindly."
          ],
          [
            143,
            "            try {"
          ],
          [
            144,
            "              observed = await options.transport.read(activeSession)"
          ],
          [
            145,
            "            } catch {"
          ],
          [
            146,
            "              break"
          ],
          [
            147,
            "            }"
          ]
        ]
      },
      {
        "title": "Чтение привязывает настройки к cookie браузера",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/DevicePreferencesController.java",
        "language": "java",
        "lines": [
          [
            21,
            "    @Override"
          ],
          [
            22,
            "    public ResponseEntity<DevicePreferencesResponse> getDevicePreferences("
          ],
          [
            23,
            "            String selector, Authentication authentication) {"
          ],
          [
            24,
            "        var context = service.read(principal(authentication), selector);"
          ],
          [
            25,
            "        return ResponseEntity.ok()"
          ],
          [
            26,
            "                .cacheControl(CacheControl.noStore())"
          ],
          [
            27,
            "                .header("
          ],
          [
            28,
            "                        HttpHeaders.SET_COOKIE,"
          ],
          [
            29,
            "                        ResponseCookie.from(COOKIE_NAME, context.selector())"
          ],
          [
            30,
            "                                .secure(true)"
          ],
          [
            31,
            "                                .httpOnly(true)"
          ],
          [
            32,
            "                                .sameSite(\"Strict\")"
          ],
          [
            33,
            "                                .path(\"/\")"
          ],
          [
            34,
            "                                .maxAge(service.ttlSeconds())"
          ],
          [
            35,
            "                                .build()"
          ],
          [
            36,
            "                                .toString())"
          ],
          [
            37,
            "                .body(context.response());"
          ]
        ]
      },
      {
        "title": "Проверка ревизии и обновление темы",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/device/DevicePreferencesService.java",
        "language": "java",
        "lines": [
          [
            73,
            "    public DevicePreferencesResponse write("
          ],
          [
            74,
            "            SessionPrincipal principal, String selector, UpdateDevicePreferencesRequest request) {"
          ],
          [
            75,
            "        long expected;"
          ],
          [
            76,
            "        try {"
          ],
          [
            77,
            "            expected = Long.parseLong(request.expectedRevision());"
          ],
          [
            78,
            "        } catch (NumberFormatException e) {"
          ],
          [
            79,
            "            throw new DevicePreferencesException(DevicePreferencesException.Code.INVALID_REQUEST);"
          ],
          [
            80,
            "        }"
          ],
          [
            81,
            "        return tx("
          ],
          [
            82,
            "                () -> {"
          ],
          [
            83,
            "                    auth.admit(principal);"
          ],
          [
            84,
            "                    Row row = lock(selector);"
          ],
          [
            85,
            "                    if (row == null || !row.expiry().isAfter(Instant.now()))"
          ],
          [
            86,
            "                        throw new DevicePreferencesException("
          ],
          [
            87,
            "                                DevicePreferencesException.Code.DEVICE_CONTEXT_LOST);"
          ],
          [
            88,
            "                    if (row.revision() != expected || row.revision() == Long.MAX_VALUE)"
          ],
          [
            89,
            "                        throw new DevicePreferencesException("
          ],
          [
            90,
            "                                DevicePreferencesException.Code.DEVICE_REVISION_CONFLICT);"
          ],
          [
            91,
            "                    jdbc.update("
          ],
          [
            92,
            "                            \"UPDATE auth_device_preferences SET theme=?,revision=revision+1,expires_at=? WHERE selector_hash=?\","
          ],
          [
            93,
            "                            request.theme(),"
          ],
          [
            94,
            "                            Timestamp.from(Instant.now().plusSeconds(ttlSeconds)),"
          ],
          [
            95,
            "                            hash(selector));"
          ],
          [
            96,
            "                    return new DevicePreferencesResponse("
          ],
          [
            97,
            "                            request.theme(), Long.toString(row.revision() + 1));"
          ],
          [
            98,
            "                });"
          ]
        ]
      },
      {
        "title": "PUT /api/auth/device-preferences · запрос",
        "language": "json",
        "example": [
          "{",
          "  \"theme\": \"dark\",",
          "  \"expectedRevision\": \"3\"",
          "}"
        ]
      },
      {
        "title": "HTTP 200 · сохранённая тема устройства",
        "language": "json",
        "example": [
          "{",
          "  \"theme\": \"dark\",",
          "  \"revision\": \"4\"",
          "}"
        ]
      }
    ]
  },
  {
    "id": "profile-device-settings-permissions",
    "title": "Геолокация и камера",
    "description": "Переключатель определяет, может ли приложение пользоваться функцией. Кнопка проверки запрашивает разрешение у платформы. В PWA статус зависит от возможностей браузера. Если браузер или система запретили доступ, разрешение меняют в их настройках. Mini App читает геолокацию через Telegram, если доступен LocationManager, и может открыть его настройки. Для встроенного сканера QR Telegram запрашивает камеру при сканировании и общего статуса доступа не сообщает.",
    "frames": [
      {
        "title": "Переключатель, проверка и переход к настройкам",
        "path": "frontends/mobile-core/src/features/profile/DeviceAccessScreen.vue",
        "language": "typescript",
        "lines": [
          [
            103,
            "          <button"
          ],
          [
            104,
            "            class=\"profile-row\""
          ],
          [
            105,
            "            type=\"button\""
          ],
          [
            106,
            "            role=\"switch\""
          ],
          [
            107,
            "            :aria-label=\"`${device.title}: использовать в приложении`\""
          ],
          [
            108,
            "            :aria-checked=\"owner.state[device.kind].enabled\""
          ],
          [
            109,
            "            @click=\"toggle(device.kind)\""
          ],
          [
            110,
            "          >"
          ],
          [
            111,
            "            {{"
          ],
          [
            112,
            "              owner.state[device.kind].enabled ? 'Включено в приложении' : 'Выключено в приложении'"
          ],
          [
            113,
            "            }}"
          ],
          [
            114,
            "          </button>"
          ],
          [
            115,
            "          <p"
          ],
          [
            116,
            "            class=\"profile-note__body\""
          ],
          [
            117,
            "            role=\"status\""
          ],
          [
            118,
            "            >{{ labels[owner.state[device.kind].permission] }}</p"
          ],
          [
            119,
            "          >"
          ],
          [
            120,
            "          <button"
          ],
          [
            121,
            "            class=\"profile-primary-button\""
          ],
          [
            122,
            "            type=\"button\""
          ],
          [
            123,
            "            :disabled=\""
          ],
          [
            124,
            "              !owner.state[device.kind].enabled ||"
          ],
          [
            125,
            "              owner.state[device.kind].permission === 'checking'"
          ],
          [
            126,
            "            \""
          ],
          [
            127,
            "            @click=\"owner.probe(device.kind)\""
          ],
          [
            128,
            "            >Запросить и проверить доступ</button"
          ],
          [
            137,
            "          <p"
          ],
          [
            138,
            "            v-if=\"device.kind === 'location' && owner.state.location.permission === 'denied'\""
          ],
          [
            139,
            "            class=\"profile-note__body\""
          ],
          [
            140,
            "            >Если система больше не показывает запрос, разреши геолокацию в настройках сайта или"
          ],
          [
            141,
            "            устройства. В Telegram используй кнопку настроек ниже, затем вернись и проверь доступ"
          ],
          [
            142,
            "            снова.</p"
          ],
          [
            143,
            "          >"
          ],
          [
            144,
            "          <button"
          ],
          [
            145,
            "            class=\"profile-secondary-button\""
          ],
          [
            146,
            "            type=\"button\""
          ],
          [
            147,
            "            @click=\"owner.inspect(device.kind)\""
          ],
          [
            148,
            "            >Обновить статус</button"
          ],
          [
            149,
            "          >"
          ],
          [
            150,
            "          <button"
          ],
          [
            151,
            "            v-if=\"device.kind === 'location' && owner.openLocationSettings\""
          ],
          [
            152,
            "            class=\"profile-secondary-button\""
          ],
          [
            153,
            "            type=\"button\""
          ],
          [
            154,
            "            @click=\"owner.openLocationSettings()\""
          ],
          [
            155,
            "            >Настройки геолокации Telegram</button"
          ],
          [
            156,
            "          >"
          ],
          [
            157,
            "          <p"
          ],
          [
            158,
            "            v-if=\"device.kind === 'camera' && owner.scanQr\""
          ],
          [
            159,
            "            class=\"profile-note__body\""
          ],
          [
            160,
            "            >Telegram запрашивает камеру при сканировании QR. Общий статус разрешения камеры он не"
          ],
          [
            161,
            "            сообщает.</p"
          ]
        ]
      },
      {
        "title": "Как браузер сообщает доступность и состояние",
        "path": "frontends/mobile-core/src/device-access/device-access.ts",
        "language": "typescript",
        "lines": [
          [
            297,
            "export async function inspectBrowserPermission("
          ],
          [
            298,
            "  kind: DeviceKind,"
          ],
          [
            299,
            "  signal?: AbortSignal,"
          ],
          [
            300,
            "): Promise<DevicePermission> {"
          ],
          [
            301,
            "  if ("
          ],
          [
            302,
            "    kind === 'location'"
          ],
          [
            303,
            "      ? !globalThis.navigator?.geolocation"
          ],
          [
            304,
            "      : !globalThis.navigator?.mediaDevices?.getUserMedia"
          ],
          [
            305,
            "  )"
          ],
          [
            306,
            "    return 'unsupported'"
          ],
          [
            307,
            "  if (!navigator.permissions?.query) return 'unknown'"
          ],
          [
            321,
            "    try {"
          ],
          [
            322,
            "      void navigator.permissions"
          ],
          [
            323,
            "        .query({ name: kind === 'location' ? 'geolocation' : ('camera' as PermissionName) })"
          ],
          [
            324,
            "        .then("
          ],
          [
            325,
            "          (value) => finish(value.state),"
          ],
          [
            326,
            "          () => finish('unknown'),"
          ],
          [
            327,
            "        )"
          ],
          [
            328,
            "    } catch {"
          ],
          [
            329,
            "      finish('unknown')"
          ]
        ]
      },
      {
        "title": "Проверка геолокации и камеры",
        "path": "frontends/mobile-core/src/device-access/device-access.ts",
        "language": "typescript",
        "lines": [
          [
            193,
            "      if (kind === 'location') {"
          ],
          [
            194,
            "        const result = await readGeolocationOnce(platform.geo, {"
          ],
          [
            195,
            "          signal: controller.signal,"
          ],
          [
            196,
            "          probe: true,"
          ],
          [
            197,
            "        })"
          ],
          [
            198,
            "        if (version !== generation || controller.signal.aborted) return"
          ],
          [
            199,
            "        state.location.permission = result.sample"
          ],
          [
            200,
            "          ? 'granted'"
          ],
          [
            201,
            "          : result.reason === 'PERMISSION_DENIED'"
          ],
          [
            202,
            "            ? 'denied'"
          ],
          [
            203,
            "            : result.reason === 'UNSUPPORTED'"
          ],
          [
            204,
            "              ? 'unsupported'"
          ],
          [
            205,
            "              : 'error'"
          ],
          [
            206,
            "      } else if (platform.scanQr) {"
          ],
          [
            207,
            "        // Telegram exposes no universal camera grant or permission request API."
          ],
          [
            208,
            "        state.camera.permission = 'unknown'"
          ],
          [
            209,
            "      } else {"
          ],
          [
            210,
            "        if (!navigator.mediaDevices?.getUserMedia) {"
          ],
          [
            211,
            "          state.camera.permission = 'unsupported'"
          ],
          [
            212,
            "          return"
          ],
          [
            213,
            "        }"
          ],
          [
            214,
            "        const streamPromise = requestBrowserCamera()"
          ]
        ]
      },
      {
        "title": "Mini App проверяет платформу и открывает настройки Telegram",
        "path": "frontends/tma-vue/src/telegram.ts",
        "language": "typescript",
        "lines": [
          [
            331,
            "    async inspect(kind, signal) {"
          ],
          [
            332,
            "      if (kind === 'camera') return nativeQr ? 'unknown' : inspectBrowserPermission(kind)"
          ],
          [
            333,
            "      if (!manager) return inspectBrowserPermission(kind)"
          ],
          [
            334,
            "      return new Promise<DevicePermission>((resolve) => {"
          ],
          [
            346,
            "        const read = () => {"
          ],
          [
            347,
            "          if (finished) return"
          ],
          [
            348,
            "          finished = true"
          ],
          [
            349,
            "          clearTimeout(timer)"
          ],
          [
            350,
            "          signal?.removeEventListener('abort', finishUnknown)"
          ],
          [
            351,
            "          resolve("
          ],
          [
            352,
            "            manager.isLocationAvailable === false"
          ],
          [
            353,
            "              ? 'unsupported'"
          ],
          [
            354,
            "              : manager.isAccessGranted"
          ],
          [
            355,
            "                ? 'granted'"
          ],
          [
            356,
            "                : manager.isAccessRequested"
          ],
          [
            357,
            "                  ? 'unknown'"
          ],
          [
            358,
            "                  : 'prompt',"
          ],
          [
            359,
            "          )"
          ],
          [
            360,
            "        }"
          ],
          [
            361,
            "        if (manager.isInited) read()"
          ],
          [
            362,
            "        else manager.init(read)"
          ],
          [
            363,
            "      })"
          ],
          [
            364,
            "    },"
          ],
          [
            365,
            "    ...(manager?.openSettings ? { openLocationSettings: () => manager.openSettings?.() } : {}),"
          ]
        ]
      }
    ]
  }
]
