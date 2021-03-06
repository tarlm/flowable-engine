package org.activiti5.engine.test.bpmn.event.timer;

/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEngineEventType;
import org.activiti.engine.repository.DeploymentProperties;
import org.activiti.engine.runtime.Clock;
import org.activiti.engine.runtime.Job;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti5.engine.impl.test.PluggableActivitiTestCase;
import org.activiti5.engine.test.api.event.TestActivitiEntityEventListener;

/**
 * @author Vasile Dirla
 */
public class StartTimerEventRepeatWithEndTest extends PluggableActivitiTestCase {

  private TestActivitiEntityEventListener listener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    listener = new TestActivitiEntityEventListener(Job.class);
    processEngineConfiguration.getEventDispatcher().addEventListener(listener);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();

    if (listener != null) {
      processEngineConfiguration.getEventDispatcher().removeEventListener(listener);
    }
  }

  /**
   * Timer repetition
   */
  public void testCycleDateStartTimerEvent() throws Exception {
    Clock clock = processEngineConfiguration.getClock();

    Calendar calendar = Calendar.getInstance();
    calendar.set(2025, Calendar.DECEMBER, 10, 0, 0, 0);
    clock.setCurrentTime(calendar.getTime());
    processEngineConfiguration.setClock(clock);

    //deploy the process
    repositoryService.createDeployment()
            .addClasspathResource("org/activiti5/engine/test/bpmn/event/timer/StartTimerEventRepeatWithEndTest.testCycleDateStartTimerEvent.bpmn20.xml")
            .deploymentProperty(DeploymentProperties.DEPLOY_AS_ACTIVITI5_PROCESS_DEFINITION, Boolean.TRUE)
            .deploy();
    assertEquals(1, repositoryService.createProcessDefinitionQuery().count());

    //AFTER DEPLOYMENT
    //when the process is deployed there will be created a timerStartEvent job which will wait to be executed.
    List<Job> jobs = managementService.createTimerJobQuery().list();
    assertEquals(1, jobs.size());

    //dueDate should be after 24 hours from the process deployment
    Calendar dueDateCalendar = Calendar.getInstance();
    dueDateCalendar.set(2025, Calendar.DECEMBER, 11, 0, 0, 0);

    //check the due date is inside the 2 seconds range
    assertEquals(true, Math.abs(dueDateCalendar.getTime().getTime() - jobs.get(0).getDuedate().getTime()) < 2000);

    //No process instances
    List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery().list();
    assertEquals(0, processInstances.size());

    //No tasks
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(0, tasks.size());

    // ADVANCE THE CLOCK
    // advance the clock to 11 dec -> the system will execute the pending job and will create a new one
    moveByMinutes(60 * 25);
    waitForJobExecutorToProcessAllJobsAndExecutableTimerJobs(2000, 200);
    
    // there must be a pending job because the endDate is not reached yet
    jobs = managementService.createTimerJobQuery().list();
    assertEquals(1, jobs.size());

    // After the first startEvent Execution should be one process instance started
    processInstances = runtimeService.createProcessInstanceQuery().list();
    assertEquals(1, processInstances.size());

    // one task to be executed (the userTask "Task A")
    tasks = taskService.createTaskQuery().list();
    assertEquals(1, tasks.size());

    dueDateCalendar = Calendar.getInstance();
    dueDateCalendar.set(2025, Calendar.DECEMBER, 12, 0, 0, 0);

    assertEquals(true, Math.abs(dueDateCalendar.getTime().getTime() - jobs.get(0).getDuedate().getTime()) < 2000);

    // ADVANCE THE CLOCK SO THE END DATE WILL BE REACHED
    // 12 dec (last execution)
    moveByMinutes(60 * 25);
    waitForJobExecutorToProcessAllJobsAndExecutableTimerJobs(2000, 200);
    
    // After the second startEvent Execution should have 2 process instances started
    // (since the first one was not completed)
    processInstances = runtimeService.createProcessInstanceQuery().list();
    assertEquals(2, processInstances.size());

    // Because the endDate 12.dec.2025 is reached
    // the current job will be deleted after execution and a new one will not be created.
    jobs = managementService.createTimerJobQuery().list();
    assertEquals(0, jobs.size());
    jobs = managementService.createJobQuery().list();
    assertEquals(0, jobs.size());

    // 2 tasks to be executed (the userTask "Task A")
    // one task for each process instance
    tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());

    // count "timer fired" events
    int timerFiredCount = 0;
    List<ActivitiEvent> eventsReceived = listener.getEventsReceived();
    for (ActivitiEvent eventReceived : eventsReceived) {
      if (ActivitiEngineEventType.TIMER_FIRED.equals(eventReceived.getType())) {
        timerFiredCount++;
      }
    }

    //count "entity created" events
    int eventCreatedCount = 0;
    for (ActivitiEvent eventReceived : eventsReceived) {
      if (ActivitiEngineEventType.ENTITY_CREATED.equals(eventReceived.getType())) {
        eventCreatedCount++;
      }
    }

    // count "entity deleted" events
    int eventDeletedCount = 0;
    for (ActivitiEvent eventReceived : eventsReceived) {
      if (ActivitiEngineEventType.ENTITY_DELETED.equals(eventReceived.getType())) {
        eventDeletedCount++;
      }
    }
    assertEquals(2, timerFiredCount); //2 timers fired
    assertEquals(4, eventCreatedCount); //2 jobs created, 2 timer and 2 executable jobs
    assertEquals(4, eventDeletedCount); //2 jobs deleted, 2 timer and 2 executable jobs

    // for each processInstance
    // let's complete the userTasks where the process is hanging in order to complete the processes.
    for (ProcessInstance processInstance : processInstances) {
      tasks = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).list();
      Task task = tasks.get(0);
      assertEquals("Task A", task.getName());
      assertEquals(1, tasks.size());
      taskService.complete(task.getId());
    }

    //now All the process instances should be completed
    processInstances = runtimeService.createProcessInstanceQuery().list();
    assertEquals(0, processInstances.size());

    //no jobs
    jobs = managementService.createTimerJobQuery().list();
    assertEquals(0, jobs.size());
    jobs = managementService.createJobQuery().list();
    assertEquals(0, jobs.size());

    //no tasks
    tasks = taskService.createTaskQuery().list();
    assertEquals(0, tasks.size());

    listener.clearEventsReceived();

    repositoryService.deleteDeployment(repositoryService.createDeploymentQuery().singleResult().getId(), true);
    
    processEngineConfiguration.resetClock();

  }

  private void moveByMinutes(int minutes) throws Exception {
    Clock clock = processEngineConfiguration.getClock();
    Date newDate = new Date(processEngineConfiguration.getClock().getCurrentTime().getTime() + ((minutes * 60 * 1000)));
    clock.setCurrentTime(newDate);
    processEngineConfiguration.setClock(clock);
  }

}
