/**
 * Copyright 2012 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */

#ifndef LINUX_KMS_H
#define LINUX_KMS_H

#ifdef __cplusplus
extern "C" {
#endif


// includes for open(2)
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include <errno.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <gbm.h>

#ifndef DRM_FORMAT_MOD_LINEAR
#define DRM_FORMAT_MOD_LINEAR 0
#endif

#ifndef DRM_FORMAT_MOD_INVALID
#define DRM_FORMAT_MOD_INVALID ((((__u64)0) << 56) | ((1ULL << 56) - 1))
#endif


struct plane {
	drmModePlane *plane;
	drmModeObjectProperties *props;
	drmModePropertyRes **props_info;
};

struct crtc {
	drmModeCrtc *crtc;
	drmModeObjectProperties *props;
	drmModePropertyRes **props_info;
};

struct connector {
	drmModeConnector *connector;
	drmModeObjectProperties *props;
	drmModePropertyRes **props_info;
};

struct gbm {
	struct gbm_device *dev;
	struct gbm_surface *surface;
	uint32_t format;
	int width, height;
};

static struct gbm gbm_init;

struct drm {
	int fd;

	/* only used for atomic: */
	struct plane *plane;
	struct crtc *crtc;
	struct connector *connector;
	int crtc_index;
	int kms_in_fence_fd;
	int kms_out_fence_fd;

	drmModeModeInfo *mode;
	uint32_t crtc_id;
	uint32_t connector_id;
};

static struct drm drm_legacy;

struct drm_fb {
	struct gbm_bo *bo;
	uint32_t fb_id;
};

struct drm_fb * drm_fb_get_from_bo(struct gbm_bo *bo);

int init_drm(struct drm *drm, const char *device);
const struct drm * init_drm_legacy(const char *device);
const struct drm * init_drm_atomic(const char *device);

static uint32_t find_crtc_for_encoder(const drmModeRes *resources,
		const drmModeEncoder *encoder) {
	int i;

	for (i = 0; i < resources->count_crtcs; i++) {
		/* possible_crtcs is a bitmask as described here:
		 * https://dvdhrm.wordpress.com/2012/09/13/linux-drm-mode-setting-api
		 */
		const uint32_t crtc_mask = 1 << i;
		const uint32_t crtc_id = resources->crtcs[i];
		if (encoder->possible_crtcs & crtc_mask) {
			return crtc_id;
		}
	}

	/* no match found */
	return -1;
}

static uint32_t find_crtc_for_connector(const struct drm *drm, const drmModeRes *resources,
		const drmModeConnector *connector) {
	int i;

	for (i = 0; i < connector->count_encoders; i++) {
		const uint32_t encoder_id = connector->encoders[i];
		drmModeEncoder *encoder = drmModeGetEncoder(drm->fd, encoder_id);

		if (encoder) {
			const uint32_t crtc_id = find_crtc_for_encoder(resources, encoder);

			drmModeFreeEncoder(encoder);
			if (crtc_id != 0) {
				return crtc_id;
			}
		}
	}

	/* no match found */
	return -1;
}


int init_drm(struct drm *drm, const char *device)
{
	drmModeRes *resources;
	drmModeConnector *connector = NULL;
	drmModeEncoder *encoder = NULL;
	int i, area;

	drm->fd = open(device, O_RDWR);

	if (drm->fd < 0) {
		printf("could not open drm device\n");
		return -1;
	}

	resources = drmModeGetResources(drm->fd);
	if (!resources) {
		printf("drmModeGetResources failed: %s\n", strerror(errno));
		return -1;
	}

	/* find a connected connector: */
	for (i = 0; i < resources->count_connectors; i++) {
		connector = drmModeGetConnector(drm->fd, resources->connectors[i]);
		if (connector->connection == DRM_MODE_CONNECTED) {
			/* it's connected, let's use this! */
			break;
		}
		drmModeFreeConnector(connector);
		connector = NULL;
	}

	if (!connector) {
		/* we could be fancy and listen for hotplug events and wait for
		 * a connector..
		 */
		printf("no connected connector!\n");
		return -1;
	}

	/* find preferred mode or the highest resolution mode: */
	for (i = 0, area = 0; i < connector->count_modes; i++) {
		drmModeModeInfo *current_mode = &connector->modes[i];

		if (current_mode->type & DRM_MODE_TYPE_PREFERRED) {
			drm->mode = current_mode;
		}

		int current_area = current_mode->hdisplay * current_mode->vdisplay;
		if (current_area > area) {
			drm->mode = current_mode;
			area = current_area;
		}
	}

	if (!drm->mode) {
		printf("could not find mode!\n");
		return -1;
	}

	/* find encoder: */
	for (i = 0; i < resources->count_encoders; i++) {
		encoder = drmModeGetEncoder(drm->fd, resources->encoders[i]);
		if (encoder->encoder_id == connector->encoder_id)
			break;
		drmModeFreeEncoder(encoder);
		encoder = NULL;
	}

	if (encoder) {
		drm->crtc_id = encoder->crtc_id;
	} else {
		uint32_t crtc_id = find_crtc_for_connector(drm, resources, connector);
		if (crtc_id == 0) {
			printf("no crtc found!\n");
			return -1;
		}

		drm->crtc_id = crtc_id;
	}

	for (i = 0; i < resources->count_crtcs; i++) {
		if (resources->crtcs[i] == drm->crtc_id) {
			drm->crtc_index = i;
			break;
		}
	}

	drmModeFreeResources(resources);

	drm->connector_id = connector->connector_id;

	return 0;
}

const struct drm * init_drm_legacy(const char *device)
{
	int ret;

	ret = init_drm(&drm_legacy, device);
	if (ret)
		return NULL;

	return &drm_legacy;
}

const struct gbm * init_gbm(int drm_fd, int w, int h, uint64_t modifier)
{
	gbm_init.dev = gbm_create_device(drm_fd);
	gbm_init.format = GBM_FORMAT_XRGB8888;

#ifndef HAVE_GBM_MODIFIERS
	if (modifier != DRM_FORMAT_MOD_INVALID) {
		fprintf(stderr, "Modifiers requested but support isn't available\n");
		return NULL;
	}
	gbm_init.surface = gbm_surface_create(gbm_init.dev, w, h,
			gbm_init.format,
			GBM_BO_USE_SCANOUT | GBM_BO_USE_RENDERING);
#else
	uint64_t *mods;
	int count;
	if (modifier != DRM_FORMAT_MOD_INVALID) {
		count = 1;
		mods = &modifier;
	} else {
		count = get_modifiers(&mods);
	}
	gbm_init.surface = gbm_surface_create_with_modifiers(gbm_init.dev, w, h,
			gbm_init.format, mods, count);
#endif

	if (!gbm_init.surface) {
		printf("failed to create gbm surface\n");
		return NULL;
	}
		
	gbm_init.width = w;
	gbm_init.height = h;

	return &gbm_init;
}

#ifdef __cplusplus
}
#endif

#endif
