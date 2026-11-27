package com.example.everme.lab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

@RestController
@Profile("clone-lab")
@RequestMapping("/api/lab")
public class LabController {
    private final LabService lab;
    private final LabRunService runs;
    private final LabCloneService clone;
    private final OpenAiLabClient client;
    public LabController(LabService lab,LabRunService runs,LabCloneService clone,OpenAiLabClient client){this.lab=lab;this.runs=runs;this.clone=clone;this.client=client;}
    public record UserRequest(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=3000) String seed){}
    public record TopicRequest(@Size(max=1000) String topic){}
    public record SessionRequest(UUID batchId){}
    public record QuestionRequest(@NotNull UUID sessionId,@NotBlank @Size(max=4000) String question){}
    public record OwnerMessageRequest(UUID batchId,@Size(max=1000) String topic,@NotBlank @Size(max=8000) String content){}
    @GetMapping("/catalog") public JsonNode catalog(){return lab.catalog();}
    @GetMapping("/config") public JsonNode config(){return lab.config();}
    @PostMapping("/models/check") public JsonNode checkModels(){return client.checkModels();}
    @GetMapping("/users") public JsonNode users(){return lab.users();}
    @PostMapping("/users") public JsonNode create(@Valid @RequestBody UserRequest request){return lab.createUser(request.name(),request.seed());}
    @GetMapping("/users/{user}/profile") public JsonNode profile(@PathVariable UUID user){return lab.profile(user);}
    @GetMapping("/users/{user}/export") public JsonNode export(@PathVariable UUID user){return lab.export(user);}
    @GetMapping("/users/{user}/batches") public JsonNode batches(@PathVariable UUID user){return lab.batches(user);}
    @PostMapping("/users/{user}/batches") public JsonNode generate(@PathVariable UUID user,@Valid @RequestBody TopicRequest request){return lab.generate(user,request.topic(),false);}
    @GetMapping("/users/{user}/batches/{batch}") public JsonNode batch(@PathVariable UUID user,@PathVariable UUID batch){return lab.batch(user,batch);}
    @PostMapping("/users/{user}/batches/{batch}/turns") public JsonNode append(@PathVariable UUID user,@PathVariable UUID batch){return lab.append(user,batch);}
    @PostMapping("/users/{user}/messages") public JsonNode message(@PathVariable UUID user,@Valid @RequestBody OwnerMessageRequest request){return lab.message(user,request.batchId(),request.topic(),request.content());}
    @PostMapping("/users/{user}/batches/{batch}/analysis") public JsonNode analyze(@PathVariable UUID user,@PathVariable UUID batch){return lab.analyze(user,batch,false);}
    @PostMapping("/users/{user}/batches/{batch}/save") public JsonNode save(@PathVariable UUID user,@PathVariable UUID batch){return lab.save(user,batch,false);}
    @PostMapping("/users/{user}/runs") public JsonNode run(@PathVariable UUID user,@Valid @RequestBody TopicRequest request){return runs.start(user,request.topic(),false);}
    @GetMapping("/users/{user}/runs/current") public JsonNode runState(@PathVariable UUID user){return runs.state(user);}
    @PostMapping("/users/{user}/runs/current/stop") public JsonNode stop(@PathVariable UUID user){return runs.stop(user);}
    @PostMapping("/users/{user}/runs/current/resume") public JsonNode resume(@PathVariable UUID user){return runs.start(user,null,true);}
    @PostMapping("/users/{user}/sessions") public JsonNode session(@PathVariable UUID user,@RequestBody SessionRequest request){return clone.start(user,request.batchId());}
    @GetMapping("/users/{user}/sessions/current") public JsonNode history(@PathVariable UUID user){return clone.history(user);}
    @PostMapping("/users/{user}/questions") public JsonNode question(@PathVariable UUID user,@Valid @RequestBody QuestionRequest request){return clone.answer(user,request.sessionId(),request.question());}
}
