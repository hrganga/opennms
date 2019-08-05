/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2013-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.poller.monitors;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.opennms.core.utils.ParameterMap;
import org.opennms.core.utils.PropertiesUtils;
import org.opennms.core.utils.TimeoutTracker;
import org.opennms.netmgt.poller.Distributable;
import org.opennms.netmgt.poller.DistributionContext;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.PollStatus;
import org.opennms.protocols.vmware.VmwareViJavaAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.vmware.vim25.AlarmState;
import com.vmware.vim25.HostRuntimeInfo;
import com.vmware.vim25.HostSystemPowerState;
import com.vmware.vim25.ManagedEntityStatus;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * The Class VmwareMonitor
 * <p/>
 * This class represents a monitor for Vmware related queries
 *
 * @author Christian Pape <Christian.Pape@informatik.hs-fulda.de>
 */
@Distributable(DistributionContext.DAEMON)
public class VmwareMonitor extends AbstractVmwareMonitor {
    /**
     * valid states for vSphere alarms
     */
    private static final Set<String> VALID_VSPHERE_ALARM_STATES = Sets.newHashSet("red", "yellow", "green", "gray");

    /**
     * logging for VMware monitor
     */
    private final Logger logger = LoggerFactory.getLogger(VmwareMonitor.class);

    /*
    * default retries
    */
    private static final int DEFAULT_RETRY = 0;

    /*
     * default timeout
     */
    private static final int DEFAULT_TIMEOUT = 3000;

    /**
     * This method queries the Vmware vCenter server for sensor data.
     *
     * @param svc        the monitored service
     * @param parameters the parameter map
     * @return the poll status for this system
     */
    @Override
    public PollStatus poll(MonitoredService svc, Map<String, Object> parameters) {
        final boolean ignoreStandBy = ParameterMap.getKeyedBoolean(parameters, "ignoreStandBy", false);

        final List<String> severitiesToReport =
                Splitter.on(",")
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(ParameterMap.getKeyedString(parameters, "reportAlarms", ""))
                    .stream()
                    .filter(e -> VALID_VSPHERE_ALARM_STATES.contains(e))
                    .collect(Collectors.toList());

        final String vmwareManagementServer = ParameterMap.getKeyedString(parameters, VMWARE_MANAGEMENT_SERVER_KEY, null);
        final String vmwareManagedEntityType = ParameterMap.getKeyedString(parameters, VMWARE_MANAGED_ENTITY_TYPE_KEY, null);
        final String vmwareManagedObjectId = ParameterMap.getKeyedString(parameters, VMWARE_MANAGED_OBJECT_ID_KEY, null);
        final String vmwareMangementServerUsername = ParameterMap.getKeyedString(parameters, VMWARE_MANAGEMENT_SERVER_USERNAME_KEY, null);
        final String vmwareMangementServerPassword = ParameterMap.getKeyedString(parameters, VMWARE_MANAGEMENT_SERVER_PASSWORD_KEY, null);

        final TimeoutTracker tracker = new TimeoutTracker(parameters, DEFAULT_RETRY, DEFAULT_TIMEOUT);

        PollStatus serviceStatus = PollStatus.unknown();

        for (tracker.reset(); tracker.shouldRetry() && !serviceStatus.isAvailable(); tracker.nextAttempt()) {

            final VmwareViJavaAccess vmwareViJavaAccess = new VmwareViJavaAccess(vmwareManagementServer, vmwareMangementServerUsername, vmwareMangementServerPassword);
            try {
                vmwareViJavaAccess.connect();
            } catch (MalformedURLException | RemoteException e) {
                logger.warn("Error connecting VMware management server '{}': '{}' exception: {} cause: '{}'", vmwareManagementServer, e.getMessage(), e.getClass().getName(), e.getCause());
                return PollStatus.unavailable("Error connecting VMware management server '" + vmwareManagementServer + "'");
            }

            if (!vmwareViJavaAccess.setTimeout(tracker.getConnectionTimeout())) {
                logger.warn("Error setting connection timeout for VMware management server '{}'", vmwareManagementServer);
            }

            String powerState = "unknown";
            Map<String, Long> alarmCountMap;

            if ("HostSystem".equals(vmwareManagedEntityType)) {
                HostSystem hostSystem = vmwareViJavaAccess.getHostSystemByManagedObjectId(vmwareManagedObjectId);
                if (hostSystem == null) {
                    return PollStatus.unknown("hostSystem=null");
                } else {
                    HostRuntimeInfo hostRuntimeInfo = hostSystem.getRuntime();
                    if (hostRuntimeInfo == null) {
                        return PollStatus.unknown("hostRuntimeInfo=null");
                    } else {
                        HostSystemPowerState hostSystemPowerState = hostRuntimeInfo.getPowerState();
                        if (hostSystemPowerState == null) {
                            return PollStatus.unknown("hostSystemPowerState=null");
                        } else {
                            powerState = hostSystemPowerState.toString();
                            alarmCountMap = getUnacknowledgedAlarmCountForEntity(hostSystem, severitiesToReport);
                        }
                    }
                }
            } else {
                if ("VirtualMachine".equals(vmwareManagedEntityType)) {
                    VirtualMachine virtualMachine = vmwareViJavaAccess.getVirtualMachineByManagedObjectId(vmwareManagedObjectId);
                    if (virtualMachine == null) {
                        return PollStatus.unknown("virtualMachine=null");
                    } else {
                        VirtualMachineRuntimeInfo virtualMachineRuntimeInfo = virtualMachine.getRuntime();
                        if (virtualMachineRuntimeInfo == null) {
                            return PollStatus.unknown("virtualMachineRuntimeInfo=null");
                        } else {
                            VirtualMachinePowerState virtualMachinePowerState = virtualMachineRuntimeInfo.getPowerState();
                            if (virtualMachinePowerState == null) {
                                return PollStatus.unknown("virtualMachinePowerState=null");
                            } else {
                                powerState = virtualMachinePowerState.toString();
                                alarmCountMap = getUnacknowledgedAlarmCountForEntity(virtualMachine, severitiesToReport);
                            }
                        }
                    }
                } else {
                    logger.warn("Error getting '{}' for '{}'", vmwareManagedEntityType, vmwareManagedObjectId);

                    vmwareViJavaAccess.disconnect();

                    return serviceStatus;
                }
            }

            final boolean anyUnacknowledgedAlarms = severitiesToReport.size() > 0 && alarmCountMap != null && alarmCountMap.size() > 0;
            final String alarmCountString = getMessageForAlarmCountMap(alarmCountMap);

            if ("poweredOn".equals(powerState)) {
                serviceStatus = anyUnacknowledgedAlarms ? PollStatus.unavailable(alarmCountString) : PollStatus.available();
            } else {
                if (ignoreStandBy && "standBy".equals(powerState)) {
                    serviceStatus = anyUnacknowledgedAlarms ? PollStatus.unavailable(alarmCountString) : PollStatus.available();
                } else {
                    serviceStatus = PollStatus.unavailable("The system's state is '" + powerState + "'" + (anyUnacknowledgedAlarms ? ", " + alarmCountString : ""));
                }
            }

            vmwareViJavaAccess.disconnect();
        }

        return serviceStatus;
    }

    private Map<String, Long> getUnacknowledgedAlarmCountForEntity(final ManagedEntity managedEntity, final List<String> severitiesToReport) {
        return Arrays.stream(managedEntity.getDeclaredAlarmState()).filter(s -> !s.acknowledged && severitiesToReport.contains(s.overallStatus.name())).collect(Collectors.groupingBy(s -> s.overallStatus.name(), Collectors.counting()));
    }

    private String getMessageForAlarmCountMap(final Map<String, Long> alarmCountMap) {
        StringBuilder alarmCountString = new StringBuilder();
        for (final String alarmSeverity : VALID_VSPHERE_ALARM_STATES) {
            if (alarmCountMap.containsKey(alarmSeverity)) {
                alarmCountString.append(alarmCountString.length() > 0 ? ", " : "").append(alarmCountMap.get(alarmSeverity)).append(" ").append(alarmSeverity);
            }
        }
        return "Alarms: " + alarmCountString;
    }
}
