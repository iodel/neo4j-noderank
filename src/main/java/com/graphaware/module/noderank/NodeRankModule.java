package com.graphaware.module.noderank;

import com.graphaware.runtime.metadata.NodeBasedContext;
import com.graphaware.runtime.module.BaseRuntimeModule;
import com.graphaware.runtime.module.TimerDrivenModule;

import com.graphaware.runtime.walk.NodeSelector;
import com.graphaware.runtime.walk.RandomNodeSelector;
import com.graphaware.runtime.walk.RandomRelationshipSelector;
import com.graphaware.runtime.walk.RelationshipSelector;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TimerDrivenModule} that perpetually walks the graph by randomly following relationships and increments
 * a node property called <code>pageRank</code> as it goes.
 * <p/>
 * Sooner or later, depending on the size and shape of the network, it will converge to values that would be computed
 * by PageRank algorithm (not normalised).
 *
 * todo normalise?
 * todo what about hyperjumps?
 */
public class NodeRankModule extends BaseRuntimeModule implements TimerDrivenModule<NodeBasedContext> {

    private static final Logger LOG = LoggerFactory.getLogger(NodeRankModule.class);

    /**
     * The name of the property that is maintained on visited nodes by this module.
     */
    public static final String NODE_RANK_PROPERTY_KEY = "nodeRank";

    private NodeSelector nodeSelector;
    private RelationshipSelector relationshipSelector;

    /**
     * Constructs a new {@link NodeRankModule} with the given ID using the default module configuration.
     *
     * @param moduleId The unique identifier for this module instance in the {@link com.graphaware.runtime.GraphAwareRuntime}.
     */
    public NodeRankModule(String moduleId) {
        this(moduleId, NodeRankModuleConfiguration.defaultConfiguration());
    }

    /**
     * Constructs a new {@link NodeRankModule} with the given ID and configuration settings.
     *
     * @param moduleId The unique identifier for this module instance in the {@link com.graphaware.runtime.GraphAwareRuntime}.
     * @param config   The {@link NodeRankModuleConfiguration} to use.
     */
    public NodeRankModule(String moduleId, NodeRankModuleConfiguration config) {
        super(moduleId);
        this.nodeSelector = new RandomNodeSelector(config.getNodeInclusionStrategy());
        this.relationshipSelector = new RandomRelationshipSelector(config.getRelationshipInclusionStrategy(), config.getNodeInclusionStrategy());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        //nothing needed for now
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeBasedContext createInitialContext(GraphDatabaseService database) {
        Node node = nodeSelector.selectNode(database);

        if (node == null) {
            LOG.warn("NodeRank did not find a node to start with. There are no nodes matching the configuration.");
            return null;
        }

        LOG.info("Starting node rank graph walker from random start node...");
        return new NodeBasedContext(node.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeBasedContext doSomeWork(NodeBasedContext lastContext, GraphDatabaseService database) {
        Node lastNode = determineLastNode(lastContext, database);
        Node nextNode = determineNextNode(lastNode, database);

        if (nextNode == null) {
            return null;
        }

        nextNode.setProperty(NODE_RANK_PROPERTY_KEY, (int) nextNode.getProperty(NODE_RANK_PROPERTY_KEY, 0) + 1);

        return new NodeBasedContext(nextNode);
    }

    private Node determineLastNode(NodeBasedContext lastContext, GraphDatabaseService database) {
        if (lastContext == null) {
            LOG.debug("No context found. Will start from a random node.");
            return null;
        }

        try {
            return lastContext.find(database);
        } catch (NotFoundException e) {
            LOG.warn("Node referenced in last context with ID: {} was not found in the database.  Will start from a random node.");
            return null;
        }

    }

    private Node determineNextNode(Node currentNode, GraphDatabaseService database) {
        if (currentNode == null) {
            return nodeSelector.selectNode(database);
        }

        Relationship randomRelationship = relationshipSelector.selectRelationship(currentNode);
        if (randomRelationship == null) {
            LOG.warn("NodeRank did not find a relationship to follow. Will start from a random node.");
            return null;
        }

        return randomRelationship.getOtherNode(currentNode);
    }

}