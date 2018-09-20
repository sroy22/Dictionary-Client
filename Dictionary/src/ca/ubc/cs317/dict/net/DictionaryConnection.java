package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.exception.DictConnectionException;
import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

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

            String welcomeMessage = input.readLine(); // receive incoming message
            String welcomeMessageStatus = welcomeMessage.split(" ")[0]; // TODO close connection and throw error if not status 220
            System.out.println(welcomeMessageStatus);

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

        try {
            output.println("quit");
            System.out.println(input.readLine()); // bye message
            socket.close();
        } catch (Exception e) {
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

        try {
            output.println("define " + database.getName() + " " + word);
            String fromServer;
            Definition definitionToAdd = new Definition("", new Database("", "")); // empty Definition
            String definition = "";
            while ((fromServer = input.readLine()) != null) {
                System.out.println("Server: " + fromServer);
                if (fromServer.contains("151")) {
                    String serverResponse[] = fromServer.split("\""); // split 151 "apple" database "database description"
                    String wordSearched = serverResponse[1]; // get apple from "apple"
                    String databaseSearched = serverResponse[2].replaceAll(" ", ""); // get rid of space beside database name
                    String databaseDescription = serverResponse[3];
                    definitionToAdd = new Definition(wordSearched, new Database(databaseSearched, databaseDescription));
                } else if (fromServer.contains("250")) {
                    break;
                } else if (fromServer.equals(".")) {
                    definitionToAdd.setDefinition(definition);
                    System.out.println(definition);
                    set.add(definitionToAdd);
                } else if (fromServer.contains("150")) { // empty for now; TODO change all if statements into switch
                                                                        // TODO     cases in another handle method
                } else {
                    definition = definition.concat(fromServer + "\n");
                }
            }
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

        try {
            output.println("match " + database.getName() + " " + strategy.getName() + " " + word);
            String fromServer;
            while ((fromServer = input.readLine()) != null) {
                if (fromServer.contains("\"")) { // all match have quotes for description
                    String matchActual = fromServer.split("\"")[1];
                    set.add(matchActual);
                }
                System.out.println("Server: " + fromServer);
                if (fromServer.contains("250")) {
                    break;
                }
            }
        } catch (Exception e) {
            throw new DictConnectionException(e);
        }

        return set;
    }

    // TODO finish implementing
    private void handleStatusCodes(int statusCode) throws Exception {
        switch (statusCode) {
            case 550:
                break;
            case 551:
                break;
            case 552:
                break;
            case 152:
                break;
            case 250:
                break;
            default:
                throw new DictConnectionException();
        }
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
        System.out.println("\nListing all databases... \n");
        String fromServer;
        try {
            while ((fromServer = input.readLine()) != null) {
                if (fromServer.contains("\"")) { // all dictionaries have quotes for description
                    String databaseName = fromServer.split(" ")[0];
                    String databaseDescription = fromServer.split("\"")[1];
                    databaseMap.put(databaseName, new Database(databaseName, databaseDescription));
                }
                System.out.println("Server: " + fromServer);
                if (fromServer.contains("250")) {
                    break;
                }
            }
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
        System.out.println("\nListing all strategies... \n");
        String fromServer;
        try {
            while ((fromServer = input.readLine()) != null) {
                if (fromServer.contains("\"")) { // all strategies have quotes for description
                    String matchingStrategyName = fromServer.split(" ")[0];
                    String matchingStrategyDescription = fromServer.split("\"")[1];
                    set.add(new MatchingStrategy(matchingStrategyName, matchingStrategyDescription));
                }
                System.out.println("Server: " + fromServer);
                if (fromServer.contains("250")) {
                    break;
                }
            }
        } catch (Exception e) {
            throw new DictConnectionException(e);
        }

        return set;
    }
}