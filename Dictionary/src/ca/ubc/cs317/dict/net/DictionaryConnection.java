package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.exception.DictConnectionException;
import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;
import ca.ubc.cs317.dict.util.DictStringParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private Map<String, Database> databaseMap = new LinkedHashMap<String, Database>();

    /**
     * Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     *                                 don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {

        try {
            socket = new Socket(host, port);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);

            Status welcomeStatus = Status.readStatus(input);
            handleStatus(welcomeStatus);
        } catch (Exception e) {
            throw new DictConnectionException(e);
        }
    }

    /**
     * Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     *                                 don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /**
     * Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     */
    public synchronized void close() {
        output.println("quit");

        try {
            socket.close();
        } catch (Exception e) {
            // ignore all exceptions
        }
    }

    /**
     * Requests and retrieves all definitions for a specific word.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        getDatabaseList(); // Ensure the list of databases has been populated

        output.println("define " + database.getName() + " " + formatWord(word));
        Definition definitionToAdd;

        try {
            Status definitionRetrievedStatus = Status.readStatus(input);
            if (!handleStatus(definitionRetrievedStatus)) {
                return set;
            }

            String numDefinitionsRetrievedString = definitionRetrievedStatus.getDetails().split(" ")[0];
            int numDefinitionsRetrieved = Integer.parseInt(numDefinitionsRetrievedString);
            for (int i = 0; i < numDefinitionsRetrieved; i++) {

                Status definitionStatus = Status.readStatus(input); // read word and database
                handleStatus(definitionStatus);

                String[] parsedStrings = DictStringParser.splitAtoms(definitionStatus.getDetails()); // handle word/db
                String wordToAdd = parsedStrings[0];
                String databaseName = parsedStrings[1];
                definitionToAdd = new Definition(wordToAdd, databaseMap.get(databaseName));

                String fromServer; // read through actual definition
                String definition = "";
                while ((fromServer = input.readLine()) != null) {
                    if (!fromServer.equals(".")) {
                        definition = definition.concat(fromServer + "\n");
                    } else {
                        definitionToAdd.setDefinition(definition);
                        set.add(definitionToAdd);
                        break;
                    }
                }
            }

            Status completionStatus = Status.readStatus(input);
            handleStatus(completionStatus);
        } catch (Exception e) {
            throw new DictConnectionException(e);
        }

        return set;
    }

    /**
     * Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        output.println("match " + database.getName() + " " + strategy.getName() + " " + formatWord(word));

        try {
            Status matchStatus = Status.readStatus(input);
            if (!handleStatus(matchStatus)) {
                return set;
            }

            String fromServer;
            while ((fromServer = input.readLine()) != null) {
                String[] parsedStrings = DictStringParser.splitAtoms(fromServer);

                if (parsedStrings.length > 1) {
                    String match = parsedStrings[1];
                    set.add(match);
                } else { // when . is encountered
                    break;
                }
            }

            Status completionStatus = Status.readStatus(input);
            handleStatus(completionStatus);
        } catch (Exception e) {
            throw new DictConnectionException(e);
        }

        return set;
    }

    /**
     * Requests and retrieves a list of all valid databases used in the server. In addition to returning the list, this
     * method also updates the local databaseMap field, which contains a mapping from database name to Database object,
     * to be used by other methods (e.g., getDefinitionMap) to return a Database object based on the name.
     *
     * @return A collection of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Database> getDatabaseList() throws DictConnectionException {

        if (!databaseMap.isEmpty()) return databaseMap.values();

        output.println("show db");

        try {
            Status databaseStatus = Status.readStatus(input);
            if (!handleStatus(databaseStatus)) {
                return databaseMap.values();
            }

            String fromServer;
            while ((fromServer = input.readLine()) != null) {
                String[] parsedStrings = DictStringParser.splitAtoms(fromServer);

                if (parsedStrings.length > 1) {
                    String databaseName = parsedStrings[0];
                    String databaseDescription = parsedStrings[1];
                    databaseMap.put(databaseName, new Database(databaseName, databaseDescription));
                } else { // when . is encountered
                    break;
                }
            }

            Status completionStatus = Status.readStatus(input);
            handleStatus(completionStatus);
        } catch (Exception e) {
            throw new DictConnectionException(e);
        }

        return databaseMap.values();
    }

    /**
     * Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        output.println("show strat");

        try {
            Status strategyStatus = Status.readStatus(input);
            if (!handleStatus(strategyStatus)) {
                return set;
            }

            String fromServer;
            while ((fromServer = input.readLine()) != null) {
                String[] parsedStrings = DictStringParser.splitAtoms(fromServer);

                if (parsedStrings.length > 1) {
                    String matchingStrategyName = parsedStrings[0];
                    String matchingStrategyDescription = parsedStrings[1];
                    set.add(new MatchingStrategy(matchingStrategyName, matchingStrategyDescription));
                } else { // when . is encountered
                    break;
                }
            }

            Status completionStatus = Status.readStatus(input);
            handleStatus(completionStatus);
        } catch (Exception e) {
            throw new DictConnectionException(e);
        }

        return set;
    }

    private String formatWord(String word) {
        return word.contains(" ") ?
                "\"" + word + "\"" :
                word;
    }

    // TODO finish implementing

    /**
     *
     * @param status
     * @return true if need to parse server response, false otherwise
     * @throws Exception
     */
    private boolean handleStatus(Status status) throws Exception {
        System.out.println(status.getStatusCode() + " " + status.getDetails());
        switch (status.getStatusType()) {
            case 1:
                break;
            case 2:
                switch (status.getStatusCode()) { // TODO use if statement if it's better
                    case 220:
                        break;
                    case 250:
                        break;
                    default:
                        throw new DictConnectionException("Invalid status code");
                }
                break;
            case 3:
                break;
            case 4:
                break;
            case 5:
                switch (status.getStatusCode()) {
                    case 500:
                        break;
                    case 501:
                        break;
                    case 502:
                        break;
                    case 503:
                        break;
                    case 552:
                    case 554:
                    case 555:
                        return false;
                    default:
                        throw new DictConnectionException();
                }
                break;
            default:
                throw new DictConnectionException("Invalid status type");
//            case 220:
//            case 550:
//            case 551:
//            case 552:
//            case 152:
//            case 250:
        }
        return true;
    }
}