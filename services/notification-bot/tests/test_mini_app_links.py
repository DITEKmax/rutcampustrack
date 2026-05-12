from bot.services.mini_app_links import build_mini_app_button, build_mini_app_url


def test_build_mini_app_url_adds_startapp_for_telegram_deep_link():
    assert (
        build_mini_app_url("https://t.me/ruttrack_bot/ruttrack", "checkin-101")
        == "https://t.me/ruttrack_bot/ruttrack?startapp=checkin-101"
    )


def test_build_mini_app_url_adds_tg_start_param_for_direct_web_app_url():
    assert (
        build_mini_app_url("https://ruttrack.site/mini-app/", "checkin-101")
        == "https://ruttrack.site/mini-app/?tgWebAppStartParam=checkin-101"
    )


def test_build_mini_app_url_preserves_existing_query_and_replaces_old_start_param():
    assert (
        build_mini_app_url("https://ruttrack.site/mini-app/?utm=bot&startapp=old", "schedule")
        == "https://ruttrack.site/mini-app/?utm=bot&tgWebAppStartParam=schedule"
    )


def test_build_mini_app_url_returns_none_for_blank_or_invalid_url():
    assert build_mini_app_url("", "checkin-1") is None
    assert build_mini_app_url(None, "checkin-1") is None
    assert build_mini_app_url("not-a-url", "checkin-1") is None
    assert build_mini_app_url("http://ruttrack.site/mini-app/", "checkin-1") is None


def test_build_mini_app_url_keeps_base_url_without_start_param():
    assert build_mini_app_url("https://ruttrack.site/mini-app/") == "https://ruttrack.site/mini-app/"


def test_build_mini_app_button_uses_url_for_telegram_deep_link():
    button = build_mini_app_button(
        text="Open",
        base_url="https://t.me/ruttrack_bot/ruttrack",
        start_param="checkin-7",
    )

    assert button is not None
    assert button.url == "https://t.me/ruttrack_bot/ruttrack?startapp=checkin-7"
    assert button.web_app is None


def test_build_mini_app_button_uses_web_app_for_direct_url():
    button = build_mini_app_button(
        text="Open",
        base_url="https://ruttrack.site/mini-app/",
        start_param="checkin-7",
    )

    assert button is not None
    assert button.url is None
    assert button.web_app is not None
    assert button.web_app.url == "https://ruttrack.site/mini-app/?tgWebAppStartParam=checkin-7"
