package org.drools.persistence.processinstance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;

import org.drools.WorkingMemory;
import org.drools.process.instance.ProcessInstance;
import org.drools.process.instance.WorkItem;
import org.drools.process.instance.WorkItemManager;
import org.drools.process.instance.impl.WorkItemImpl;
import org.drools.runtime.process.WorkItemHandler;

public class JPAWorkItemManager implements WorkItemManager {

    private EntityManager manager;
    private WorkingMemory workingMemory;
	private Map<String, WorkItemHandler> workItemHandlers = new HashMap<String, WorkItemHandler>();
    
    public JPAWorkItemManager(WorkingMemory workingMemory) {
    	this.workingMemory = workingMemory;
    }
    
    public void setEntityManager(EntityManager manager) {
        this.manager = manager;
    }

	public void internalExecuteWorkItem(WorkItem workItem) {
		WorkItemInfo workItemInfo = new WorkItemInfo(workItem);
        manager.persist(workItemInfo);
        ((WorkItemImpl) workItem).setId(workItemInfo.getId());
        workItemInfo.update();
        WorkItemHandler handler = (WorkItemHandler) this.workItemHandlers.get(workItem.getName());
	    if (handler != null) {
	        handler.executeWorkItem(workItem, this);
	    } else {
	        System.err.println("Could not find work item handler for " + workItem.getName());
	    }
	}

	public void internalAbortWorkItem(long id) {
        WorkItemInfo workItemInfo = manager.find(WorkItemInfo.class, id);
        // work item may have been aborted
        if (workItemInfo != null) {
        	WorkItemImpl workItem = (WorkItemImpl) workItemInfo.getWorkItem();
            WorkItemHandler handler = (WorkItemHandler) this.workItemHandlers.get(workItem.getName());
            if (handler != null) {
                handler.abortWorkItem(workItem, this);
            } else {
                System.err.println("Could not find work item handler for " + workItem.getName());
            }
            manager.remove(workItemInfo);
        }
	}

	public void internalAddWorkItem(WorkItem workItem) {
	}

    public void completeWorkItem(long id, Map<String, Object> results) {
        WorkItemInfo workItemInfo = manager.find(WorkItemInfo.class, id);
        // work item may have been aborted
        if (workItemInfo != null) {
        	WorkItemImpl workItem = (WorkItemImpl) workItemInfo.getWorkItem();
            workItem.setResults(results);
            ProcessInstance processInstance = workingMemory.getProcessInstance(workItem.getProcessInstanceId());
            workItem.setState(WorkItem.COMPLETED);
            // process instance may have finished already
            if (processInstance != null) {
                processInstance.signalEvent("workItemCompleted", workItem);
            }
            manager.remove(workItemInfo);
            workingMemory.fireAllRules();
    	}
    }

    public void abortWorkItem(long id) {
        WorkItemInfo workItemInfo = manager.find(WorkItemInfo.class, id);
        // work item may have been aborted
        if (workItemInfo != null) {
        	WorkItemImpl workItem = (WorkItemImpl) workItemInfo.getWorkItem();
            ProcessInstance processInstance = workingMemory.getProcessInstance(workItem.getProcessInstanceId());
            workItem.setState(WorkItem.ABORTED);
            // process instance may have finished already
            if (processInstance != null) {
                processInstance.signalEvent("workItemAborted", workItem);
            }
            manager.remove(workItemInfo);
            workingMemory.fireAllRules();
        }
    }

	public WorkItem getWorkItem(long id) {
		WorkItemInfo workItemInfo = manager.find(WorkItemInfo.class, id);
        if (workItemInfo == null) {
            return null;
        }
        return workItemInfo.getWorkItem();
	}

	public Set<WorkItem> getWorkItems() {
		return new HashSet<WorkItem>();
	}

	public void registerWorkItemHandler(String workItemName, WorkItemHandler handler) {
        this.workItemHandlers.put(workItemName, handler);
	}

}
