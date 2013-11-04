/*
 * Copyright (C) 2013  Ohm Data
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ohmdb.module_cfg;

import com.google.common.collect.ImmutableList;
import ohmdb.discovery.BeaconService;
import ohmdb.interfaces.DiscoveryModule;
import ohmdb.interfaces.OhmModule;
import ohmdb.interfaces.ReplicationModule;
import ohmdb.interfaces.TabletModule;
import ohmdb.replication.ReplicatorService;
import ohmdb.tablet.TabletService;
import ohmdb.util.Graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static ohmdb.messages.ControlMessages.ModuleType;

/**
 *
 */
public class ModuleDeps {

    public static void doTarjan(ImmutableList<ModuleType> seed) {

        Map<ModuleType, Graph.Node<ModuleType>> allNodes = buildDepGraph(seed);

        Graph.doTarjan(allNodes.values());
    }


    public static Map<ModuleType, Graph.Node<ModuleType>> buildDepGraph(ImmutableList<ModuleType> seed) {
        Map<ModuleType, Graph.Node<ModuleType>> nodes = new HashMap<>();
        Set<ModuleType> processed = new HashSet<>();
        Queue<ModuleType> q = new LinkedList<>();
        q.addAll(seed);

        ModuleType parent;
        while ((parent = q.poll()) != null) {
            if (processed.contains(parent))
                continue;
            else
                processed.add(parent);

            if (!nodes.containsKey(parent))
                nodes.put(parent, new Graph.Node<>(parent));

            ImmutableList<ModuleType> mdeps = getDependency(parent);
            q.addAll(mdeps);
            for (ModuleType dependent : mdeps) {
                Graph.Node<ModuleType> pNode = nodes.get(parent);
                Graph.Node<ModuleType> dNode;
                if (nodes.containsKey(dependent)) {
                    dNode = nodes.get(dependent);
                } else {
                    dNode = new Graph.Node<>(dependent);
                    nodes.put(dependent, dNode);
                }

                pNode.dependencies.add(dNode);
            }
        }
        return nodes;
    }

    public static ImmutableList<ModuleType> getDependency(ModuleType moduleType) {
        switch (moduleType) {
            case Discovery:
                return ImmutableList.of(ModuleType.Services);
            case Replication:
                return ImmutableList.of(ModuleType.Discovery);
            case Tablet:
                return ImmutableList.of(ModuleType.Replication);
            case Client:
                return ImmutableList.of(ModuleType.Tablet, ModuleType.Management);
            case Management:
                return ImmutableList.of(ModuleType.Tablet, ModuleType.Replication);
            case Services:
                return ImmutableList.of();
            default:
                throw new RuntimeException("Someone forgot to extend this switch statement");

        }
    }

    public static Class<? extends OhmModule> getImplClass(ModuleType moduleType) {
        switch (moduleType) {
            case Discovery:
                return BeaconService.class;
            case Replication:
                return ReplicatorService.class;
            case Tablet:
                return TabletService.class;
            default:
                throw new RuntimeException("Someone forgot to extend this switch statement");

        }
    }

    public static Class<? extends OhmModule> getInterface(ModuleType moduleType) {
        switch (moduleType) {
            case Discovery:
                return DiscoveryModule.class;
            case Replication:
                return ReplicationModule.class;
            case Tablet:
                return TabletModule.class;
            default:
                throw new RuntimeException("Someone forgot to extend this switch statement");
        }
    }

}
