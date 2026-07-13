package com.liu.eemrsserver.memory;

import com.liu.eemrsserver.memory.dto.MemoryContextDTO;
import com.liu.eemrsserver.memory.dto.MemoryUserScopeDTO;
import com.liu.eemrsserver.memory.dto.ShortTermMemoryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {
    @Autowired
    private PatientIdentityResolver patientIdentityResolver;
    @Autowired
    private LongTermMemoryService longTermMemoryService;
    @Autowired
    private MediumTermMemoryService mediumTermMemoryService;
    @Autowired
    private ShortTermMemoryService shortTermMemoryService;
    @Autowired
    private UserMemoryVectorService userMemoryVectorService;

    public MemoryContextDTO getContext(String sessionId, String query) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        MemoryContextDTO context = new MemoryContextDTO();
        context.setLongTermMemory(longTermMemoryService.getLongTermMemory(scope));
        context.setMediumTermMemory(mediumTermMemoryService.getMediumTermMemory(scope));
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            ShortTermMemoryDTO shortTerm = shortTermMemoryService.getSession(scope, sessionId);
            context.setShortTermMemory(shortTerm);
        }
        context.setRelatedUserMemory(userMemoryVectorService.searchUserMemory(scope, query, 5));
        return context;
    }
}
