package org.nrg.xnat.plugins.template.rest;

import io.swagger.annotations.*;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.exceptions.NrgServiceException;
import org.nrg.xapi.authorization.GuestUserAccessXapiAuthorization;
import org.nrg.xapi.rest.AbstractXapiRestController;
import org.nrg.xapi.rest.AuthDelegate;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.services.archive.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static org.nrg.xdat.security.helpers.AccessLevel.Authorizer;

@Api("CCDB REST Api")
@XapiRestController
@RequestMapping(value = "/ccdb")
public class CCDBApi extends AbstractXapiRestController {

    private final CatalogService _catalogService;
    private final SiteConfigPreferences _preferences;
    private static final Logger _log = LoggerFactory.getLogger( CCDBApi.class);
    private final Zipper _zipper;

    @Autowired
    public CCDBApi(final UserManagementServiceI userManagementServiceI,
                   final RoleHolder roleHolder,
                   final SiteConfigPreferences preferences,
                   final CatalogService catalogService) {
        super( userManagementServiceI, roleHolder);
        _preferences = preferences;
        _catalogService = catalogService;
        _zipper = new MyZipper();
    }

    @ApiOperation(value = "Upload CCDB Hotel data.", response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "Successfully uploaded CCDB Hotel session(s)."),
            @ApiResponse(code = 500, message = "An unexpected or unknown error occurred")})
    @XapiRequestMapping(value = "projects/{projectID}/hotelSessions",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE},
            method = RequestMethod.POST,
            restrictTo = Authorizer)
    @AuthDelegate(GuestUserAccessXapiAuthorization.class)
    public ResponseEntity<String> doUploadHotelData(
            @ApiParam("The project label or ID") @PathVariable final String  projectID,
            @ApiParam("Zip file with hotel-session csv and image data") @RequestParam("file") final MultipartFile file) throws NrgServiceException {
        try {
            List<File> files = _zipper.unzip(file.getInputStream());
            if( ! files.isEmpty()) {

                Collection<HotelSession> hotelSessions = HotelSession.getSessionsFromFiles( files);
                if( hotelSessions.isEmpty()) {
                    return new ResponseEntity<>("Failed to find hotel-scan csv.", HttpStatus.BAD_REQUEST);
                }
                UserI user = getSessionUser();

                HotelSessionHandler sessionHandler = new HotelSessionHandler( _preferences, _catalogService);

                sessionHandler.handleSessions( projectID, hotelSessions, user);

                return new ResponseEntity<>(HttpStatus.OK);
            }
            else {
                return new ResponseEntity<>("Zip file is empty.", HttpStatus.NO_CONTENT);
            }
        }
        catch( Exception e) {
            HttpStatus status = (e instanceof HandlerException)? ((HandlerException) e).getHttpStatus(): HttpStatus.INTERNAL_SERVER_ERROR;
            String msg = "An error occured when user " + getSessionUser().getUsername() + " tried to upload CCDB Hotel data zip file " + file.getOriginalFilename();
            msg += "\n" + e.getMessage();
            _log.error( msg, e);
            ResponseEntity<String> response = new ResponseEntity<> (msg, status);
            return response;
        }
        finally {
            try { _zipper.close();} catch (IOException e) { /* ignore */}
        }
    }
}
