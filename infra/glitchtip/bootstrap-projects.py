"""Provision only the two fixed telemetry projects in this dedicated database."""
import os
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "glitchtip.settings")
import django
django.setup()
from django.contrib.auth import get_user_model
from django.db import transaction
from apps.organizations_ext.models import Organization, OrganizationUser, OrganizationOwner
from apps.projects.models import Project, ProjectKey

with transaction.atomic():
    organization, _ = Organization.objects.get_or_create(slug="ruttrack", defaults={"name": "RutTrack"})
    for expected_id, name, platform in [(1, "rct-vue", "javascript-vue"), (2, "rct-java", "java")]:
        project, _ = Project.objects.get_or_create(organization=organization, name=name, defaults={"platform": platform})
        if project.id != expected_id:
            raise RuntimeError("Dedicated telemetry database must reserve project IDs 1 and 2")
        project.scrub_ip_addresses = True
        project.scrub_config = {"enabled": True, "scrub_emails": True}
        project.save(update_fields=["scrub_ip_addresses", "scrub_config"])
        ProjectKey.objects.filter(project=project).update(rate_limit_count=60, rate_limit_window=3600)
    # No built-in password. An explicitly created private operator can claim the UI.
    operator = get_user_model().objects.filter(is_superuser=True, is_active=True).order_by("id").first()
    if operator:
        membership, _ = OrganizationUser.objects.get_or_create(organization=organization, user=operator, defaults={"role": 0})
        OrganizationOwner.objects.get_or_create(organization=organization, defaults={"organization_user": membership})
