package org.jboss.errai.cdi.event.client.test;

import java.util.List;
import java.util.Map;

import org.jboss.errai.cdi.event.client.EventProducerTestModule;
import org.jboss.errai.enterprise.client.cdi.api.CDI;
import org.jboss.errai.ioc.client.container.IOC;

/**
 * Tests CDI event producers.
 *
 * @author Christian Sadilek <csadilek@redhat.com>
 */
public class EventProducerIntegrationTest extends AbstractEventIntegrationTest {

  @Override
  public String getModuleName() {
    return "org.jboss.errai.cdi.event.EventProducerTestModule";
  }

  @Override
  public void gwtSetUp() throws Exception {
    super.gwtSetUp();
  }

  public void testInjectedEvents() {
    delayTestFinish(60000);

      CDI.addPostInitTask(new Runnable() {
        @Override
        public void run() {
          EventProducerTestModule module = IOC.getBeanManager().lookupBean(EventProducerTestModule.class).getInstance();

          assertNotNull(module.getEvent());
          assertNotNull(module.getEventA());
          assertNotNull(module.getEventB());
          assertNotNull(module.getEventC());
          assertNotNull(module.getEventAB());
          assertNotNull(module.getEventAC());
          assertNotNull(module.getEventBC());
          assertNotNull(module.getEventABC());
          
          finishTest();
      }
    });
  }

  public void testEventProducers() {
    final Runnable verifier = new Runnable() {
      public void run() {
        EventProducerTestModule module = IOC.getBeanManager().lookupBean(EventProducerTestModule.class).getInstance();

        Map<String, List<String>> actualEvents = module.getReceivedEventsOnServer();

        // assert that the server received all events
        EventProducerIntegrationTest.this.verifyQualifiedEvents(actualEvents);
        finishTest();
      }
    };

    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        EventProducerTestModule module = IOC.getBeanManager().lookupBean(EventProducerTestModule.class).getInstance();

        if (module.getBusReadyEventsReceived()) {
          module.setResultVerifier(verifier);
          module.fireAll();
        }
        else {
          fail("Did not receive a BusReadyEvent!");
        }
      }
    });

    // only used for the case the {@see FinishEvent} was not received.
    verifyInBackupTimer(verifier, 120000);
    delayTestFinish(240000);
  }
}