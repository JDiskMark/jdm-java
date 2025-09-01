
package jdiskmark;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * Access class for the entity manager static reference
 * @author James
 */
public class EM {
    
    private static EntityManager em = null;
    
    static EntityManager getEntityManager() {
        if (em == null) {
            EntityManagerFactory emf = Persistence.createEntityManagerFactory("JDiskMarkPU");
            em = emf.createEntityManager();
        }
        return em;
    }
}
