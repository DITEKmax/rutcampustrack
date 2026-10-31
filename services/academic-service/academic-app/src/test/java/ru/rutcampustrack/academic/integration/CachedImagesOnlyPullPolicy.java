package ru.rutcampustrack.academic.integration;

import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.utility.DockerImageName;

/** Bounded group checks use only runtime-attested local images, including helper containers. */
public final class CachedImagesOnlyPullPolicy implements ImagePullPolicy {
    @Override
    public boolean shouldPull(DockerImageName imageName) {
        return false;
    }
}
