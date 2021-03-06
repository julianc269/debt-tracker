package seedu.address.commons.core;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.AclRule;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import javafx.application.Platform;
import seedu.address.commons.events.ui.NewResultAvailableEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class CalendarManager extends ComponentManager {
    private static final Logger logger = LogsCenter.getLogger(CalendarManager.class);
    private static final String APPLICATION_NAME = "Google Calendar";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String CALENDAR_TITLE = "Debt Tracker";
    private Calendar service;
    private String calendarId;
    private static CalendarManager instance;

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final String CREDENTIALS_FILE_PATH = "/creds/creds.json";

    private CalendarManager() throws IOException, GeneralSecurityException {
        /*
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        this.service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();*/

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        if(CalendarManager.getSavedCredentials(HTTP_TRANSPORT) != null) {
            this.service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, CalendarManager.getSavedCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
            String calendarId = getCalendarIdBySummary(CALENDAR_TITLE);
            if(calendarId == null) {
                createNewCalendar();
                setCalendarAccess();
            } else {
                this.calendarId = calendarId;
            }
        }
    }

    public static CalendarManager getInstance() {
        if (instance == null) {
            try {
                instance = new CalendarManager();
            }
            catch (IOException | GeneralSecurityException | NullPointerException ex){
                logger.info("Could not initialize calendar");
            }
        }
        return instance;
    }

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        GoogleAuthorizationCodeFlow flow = buildFlow(HTTP_TRANSPORT);
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    private static Credential getSavedCredentials(final NetHttpTransport HTTP_TRANSPORT){
        try {
            GoogleAuthorizationCodeFlow flow = buildFlow(HTTP_TRANSPORT);
            Credential credential = flow.loadCredential("user");
            if (credential != null
                    && (credential.getRefreshToken() != null ||
                    credential.getExpiresInSeconds() == null ||
                    credential.getExpiresInSeconds() > 60)) {
                return credential;
            }
        }
        catch(IOException | NullPointerException ex){
            logger.info("Error getting user credentials");
        }
        return null;
    }

    private static GoogleAuthorizationCodeFlow buildFlow(final NetHttpTransport HTTP_TRANSPORT) throws IOException{
        // Load client secrets.
        InputStream in = CalendarManager.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        return flow;
    }

    public boolean calendarLogin(){
        if(service == null) {
            Thread t = new Thread(new LoginRunnable());
            t.start();
            return true;
        } else {
            return false;
        }
    }

    public boolean calendarLogout(){
        try {
            Files.deleteIfExists(Paths.get("tokens/StoredCredential"));
            this.service = null;
            this.calendarId = null;
            return true;
        } catch (IOException ex){
            return false;
        }
    }


    public boolean isAuthenticated(){
        return service != null;
    }


    public Calendar getCalendarService(){
        return service;
    }

    private void createNewCalendar(){
        if(service != null){
            com.google.api.services.calendar.model.Calendar cal = new com.google.api.services.calendar.model.Calendar();
            cal.setSummary("Debt Tracker");
            cal.setTimeZone("Asia/Singapore");
            try {
                this.calendarId = service.calendars().insert(cal).execute().getId();
            }
            catch(IOException ex){
                logger.info(ex.getMessage());
            }
        }
    }

    private void setCalendarAccess(){
        AclRule rule = new AclRule();
        AclRule.Scope scope = new AclRule.Scope();
        scope.setType("default");
        rule.setScope(scope).setRole("reader");
        Platform.runLater(() -> {
            try {
                service.acl().insert(this.calendarId, rule).execute();
            } catch(IOException ex){
                logger.info(ex.getMessage());
            }
        });
    }


    public String getCalendarId(){
        return this.calendarId;
    }



    private String getCalendarIdBySummary(String summary){
        if(service == null) {
            return null;
        }
        try {
            String pageToken = null;
            do {
                CalendarList calendarList = service.calendarList().list().setPageToken(pageToken).execute();
                List<CalendarListEntry> items = calendarList.getItems();

                for (CalendarListEntry calendarListEntry : items) {
                    if(calendarListEntry.getSummary().equals(summary)){
                        return calendarListEntry.getId();
                    }
                }
                pageToken = calendarList.getNextPageToken();
            } while (pageToken != null);
            return null;
        }
        catch (IOException ex){
            return null;
        }
    }


    private class LoginRunnable implements Runnable {

        @Override
        public void run() {
            try {
                final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                Calendar c = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, CalendarManager.getCredentials(HTTP_TRANSPORT))
                        .setApplicationName(APPLICATION_NAME)
                        .build();
                CalendarManager.getInstance().service = c;
                String calendarId = getCalendarIdBySummary("Debt Tracker");
                if(calendarId == null) {
                    createNewCalendar();
                    setCalendarAccess();
                } else {
                    CalendarManager.getInstance().calendarId = calendarId;
                }
                raise(new NewResultAvailableEvent("Logged in successfully!"));
            }
            catch(IOException | GeneralSecurityException | NullPointerException ex){
                logger.info("Error getting user credentials");
                raise(new NewResultAvailableEvent("Login unsuccessful!"));
            }
        }
    }


}