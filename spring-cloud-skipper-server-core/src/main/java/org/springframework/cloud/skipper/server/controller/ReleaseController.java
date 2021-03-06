/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.skipper.server.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.skipper.PackageDeleteException;
import org.springframework.cloud.skipper.ReleaseNotFoundException;
import org.springframework.cloud.skipper.SkipperException;
import org.springframework.cloud.skipper.domain.CancelRequest;
import org.springframework.cloud.skipper.domain.CancelResponse;
import org.springframework.cloud.skipper.domain.DeleteProperties;
import org.springframework.cloud.skipper.domain.Info;
import org.springframework.cloud.skipper.domain.Manifest;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.cloud.skipper.domain.RollbackRequest;
import org.springframework.cloud.skipper.domain.UpgradeRequest;
import org.springframework.cloud.skipper.server.controller.support.InfoResourceAssembler;
import org.springframework.cloud.skipper.server.controller.support.ManifestResourceAssembler;
import org.springframework.cloud.skipper.server.controller.support.ReleaseResourceAssembler;
import org.springframework.cloud.skipper.server.service.ReleaseService;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

/**
 * REST controller for Skipper release related operations.
 *
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Janne Valkealahti
 */
@RestController
@RequestMapping("/api/release")
public class ReleaseController {

	private final ReleaseService releaseService;
	private final SkipperStateMachineService skipperStateMachineService;
	private final ReleaseResourceAssembler releaseResourceAssembler = new ReleaseResourceAssembler();
	private final ManifestResourceAssembler manifestResourceAssembler = new ManifestResourceAssembler();
	private final InfoResourceAssembler infoResourceAssembler = new InfoResourceAssembler();

	@Value("${info.app.name:#{null}}")
	private String appName;
	@Value("${info.app.version:#{null}}")
	private String appVersion;

	public ReleaseController(ReleaseService releaseService,
			SkipperStateMachineService skipperStateMachineService) {
		this.releaseService = releaseService;
		this.skipperStateMachineService = skipperStateMachineService;
	}

	@RequestMapping(method = RequestMethod.GET)
	public ReleaseControllerLinksResource resourceLinks() {
		ReleaseControllerLinksResource resource = new ReleaseControllerLinksResource();
		resource.add(ControllerLinkBuilder.linkTo(methodOn(ReleaseController.class).status(null))
				.withRel("status/name"));
		resource.add(ControllerLinkBuilder.linkTo(methodOn(ReleaseController.class).status(null, null))
				.withRel("status/name/version"));
		resource.add(
				ControllerLinkBuilder.linkTo(methodOn(ReleaseController.class).manifest(null))
						.withRel("manifest"));
		resource.add(ControllerLinkBuilder.linkTo(methodOn(ReleaseController.class).manifest(null, null))
				.withRel("manifest/name/version"));
		resource.add(ControllerLinkBuilder.linkTo(methodOn(ReleaseController.class).upgrade(null))
				.withRel("upgrade"));
		resource.add(
				ControllerLinkBuilder.linkTo(methodOn(ReleaseController.class).rollbackWithNamedVersion(null, 123))
						.withRel("rollback"));
		resource.add(ControllerLinkBuilder.linkTo(methodOn(ReleaseController.class).list())
				.withRel("list"));
		resource.add(ControllerLinkBuilder.linkTo(methodOn(ReleaseController.class).list(null))
				.withRel("list/name"));
		return resource;
	}

	// Release commands

	@RequestMapping(path = "/status/{name}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public Resource<Info> status(@PathVariable("name") String name) {
		return this.infoResourceAssembler.toResource(this.releaseService.status(name));
	}

	@RequestMapping(path = "/status/{name}/{version}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public Resource<Info> status(@PathVariable("name") String name, @PathVariable("version") Integer version) {
		return this.infoResourceAssembler.toResource(this.releaseService.status(name, version));
	}

	@RequestMapping(path = "/manifest/{name}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public Resource<Manifest> manifest(@PathVariable("name") String name) {
		return this.manifestResourceAssembler.toResource(this.releaseService.manifest(name));
	}

	@RequestMapping(path = "/manifest/{name}/{version}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public Resource<Manifest> manifest(@PathVariable("name") String name,
			@PathVariable("version") Integer version) {
		return this.manifestResourceAssembler.toResource(this.releaseService.manifest(name, version));
	}

	@RequestMapping(path = "/upgrade", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public Resource<Release> upgrade(@RequestBody UpgradeRequest upgradeRequest) {
		Release release = this.skipperStateMachineService.upgradeRelease(upgradeRequest);
		return this.releaseResourceAssembler.toResource(release);
	}

	@RequestMapping(path = "/rollback", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public Resource<Release> rollback(@RequestBody RollbackRequest rollbackRequest) {
		Release release = this.skipperStateMachineService.rollbackRelease(rollbackRequest);
		return this.releaseResourceAssembler.toResource(release);
	}

	@RequestMapping(path = "/rollback/{name}/{version}", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	@Deprecated
	public Resource<Release> rollbackWithNamedVersion(@PathVariable("name") String releaseName,
			@PathVariable("version") int rollbackVersion) {
		Release release = this.skipperStateMachineService
				.rollbackRelease(new RollbackRequest(releaseName, rollbackVersion));
		return this.releaseResourceAssembler.toResource(release);
	}

	@RequestMapping(path = "/{name}", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public Resource<Release> delete(@PathVariable("name") String releaseName) {
		return deleteRelease(releaseName, false);
	}

	@RequestMapping(path = "/{name}/package", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public Resource<Release> deleteWithPackage(@PathVariable("name") String releaseName) {
		return deleteRelease(releaseName, true);
	}

	private Resource<Release> deleteRelease(String releaseName, boolean canDeletePackage) {
		DeleteProperties deleteProperties = new DeleteProperties();
		deleteProperties.setDeletePackage(canDeletePackage);
		Release release = this.skipperStateMachineService.deleteRelease(releaseName, deleteProperties);
		return this.releaseResourceAssembler.toResource(release);
	}

	@RequestMapping(path = "/cancel", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.OK)
	public CancelResponse cancel(@RequestBody CancelRequest cancelRequest) {
		boolean accepted = this.skipperStateMachineService.cancelRelease(cancelRequest.getReleaseName());
		return new CancelResponse(accepted);
	}

	@RequestMapping(path = "/list", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public Resources<Resource<Release>> list() {
		List<Release> releaseList = this.releaseService.list();
		Resources<Resource<Release>> resources = this.releaseResourceAssembler.toResources(releaseList);
		return resources;
	}

	@RequestMapping(path = "/list/{name}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public Resources<Resource<Release>> list(@PathVariable("name") String releaseName) {
		List<Release> releaseList = this.releaseService.list(releaseName);
		Resources<Resource<Release>> resources = this.releaseResourceAssembler.toResources(releaseList);
		return resources;
	}

	@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Release not found")
	@ExceptionHandler(ReleaseNotFoundException.class)
	public void handleReleaseNotFoundException() {
		// needed for server not to log 500 errors
	}

	@ResponseStatus(value = HttpStatus.CONFLICT, reason = "Package deletion error")
	@ExceptionHandler(PackageDeleteException.class)
	public void handlePackageDeleteException() {
		// needed for server not to log 500 errors
	}

	@ResponseStatus(value = HttpStatus.CONFLICT, reason = "Skipper server exception")
	@ExceptionHandler(SkipperException.class)
	public void handleSkipperException() {
		// needed for server not to log 500 errors
	}

	/**
	 * @author Mark Pollack
	 */
	public static class ReleaseControllerLinksResource extends ResourceSupport {

		public ReleaseControllerLinksResource() {
		}
	}
}
