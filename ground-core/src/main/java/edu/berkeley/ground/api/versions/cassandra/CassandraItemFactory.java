package edu.berkeley.ground.api.versions.cassandra;

import edu.berkeley.ground.api.versions.ItemFactory;
import edu.berkeley.ground.api.versions.GroundType;
import edu.berkeley.ground.api.versions.VersionHistoryDAG;
import edu.berkeley.ground.db.CassandraClient.CassandraConnection;
import edu.berkeley.ground.db.DBClient.GroundDBConnection;
import edu.berkeley.ground.db.DbDataContainer;
import edu.berkeley.ground.exceptions.GroundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CassandraItemFactory extends ItemFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraItemFactory.class);

    private CassandraVersionHistoryDAGFactory versionHistoryDAGFactory;

    public CassandraItemFactory(CassandraVersionHistoryDAGFactory versionHistoryDAGFactory) {
        this.versionHistoryDAGFactory = versionHistoryDAGFactory;
    }

    public void insertIntoDatabase(GroundDBConnection connectionPointer, String id) throws GroundException {
        CassandraConnection connection = (CassandraConnection) connectionPointer;

        List<DbDataContainer> insertions = new ArrayList<>();
        insertions.add(new DbDataContainer("id", GroundType.STRING, id));

        connection.insert("Items", insertions);
    }

    public void update(GroundDBConnection connectionPointer, String itemId, String childId, List<String> parentIds) throws GroundException {
        // If a parent is specified, great. If it's not specified and there is only one leaf, great.
        // If it's not specified, there are either 0 or > 1 leaves, then make it a child of EMPTY.
        // Eventually, there should be a specification about empty-child?
        if (parentIds.isEmpty()) {
            List<String> leaves = this.getLeaves(connectionPointer, itemId);
            if (leaves.size() == 1) {
                parentIds.add(leaves.get(0));
            } else {
                parentIds.add("EMPTY");
            }
        }

        VersionHistoryDAG dag;
        try {
            dag = this.versionHistoryDAGFactory.retrieveFromDatabase(connectionPointer, itemId);
        } catch (GroundException e) {
            if (!e.getMessage().contains("No results found for query:")) {
                throw e;
            }

            dag = this.versionHistoryDAGFactory.create(itemId);
        }

        for (String parentId : parentIds) {
            if (!parentId.equals("EMPTY") && !dag.checkItemInDag(parentId)) {
                String errorString = "Parent " + parentId + " is not in Item " + itemId + ".";

                LOGGER.error(errorString);
                throw new GroundException(errorString);
            }
            this.versionHistoryDAGFactory.addEdge(connectionPointer, dag, parentId, childId, itemId);
        }
    }

    private List<String> getLeaves(GroundDBConnection connection, String itemId) throws GroundException {
        try {
            VersionHistoryDAG<?> dag = this.versionHistoryDAGFactory.retrieveFromDatabase(connection, itemId);

            return dag.getLeaves();
        } catch (GroundException e) {
            if (!e.getMessage().contains("No results found for query:")) {
                throw e;
            }

            return new ArrayList<>();
        }
    }

}