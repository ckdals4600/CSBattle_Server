package com.battle.csbattle.controller;

import com.battle.csbattle.battle.Battle;
import com.battle.csbattle.battle.BattleType;
import com.battle.csbattle.battle.UserStatus;
import com.battle.csbattle.dto.AnswerDto;
import com.battle.csbattle.dto.AnswerResultDto;
import com.battle.csbattle.dto.QuestionDto;
import com.battle.csbattle.dto.UserDto;
import com.battle.csbattle.response.Response;
import com.battle.csbattle.response.StatusEnum;
import com.battle.csbattle.service.BattleService;
import com.battle.csbattle.service.QuestionService;
import com.battle.csbattle.service.SseService;
import com.battle.csbattle.util.SseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.TimerTask;


@RestController
@Slf4j
public class BattleController {
    private final QuestionService questionService;

    public BattleController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping("/battle/question")
    public ResponseEntity<Response> getQuestion(
            @RequestParam("userId") String userId) {
        UserDto player = SseService.allPlayers.get(userId);
        Battle battle = player.getBattle();

        QuestionDto questionDto = battle.getQuestionByUser(userId);
        QuestionDto responseDto = QuestionDto.builder()
                .content(questionDto.getContent())
                .csCategory(questionDto.getCsCategory())
                .questionType(questionDto.getQuestionType())
                .description(questionDto.getDescription())
                .attachmentPath(questionDto.getAttachmentPath())
                .build();

        SseUtil.sendToClient(player.getEmitter(), "Question", responseDto);

        player.setUserStatus(UserStatus.AbleAnswer);
        player.getAnswerTimer().schedule(new TimerTask() {
            @Override
            public void run() {
                log.info(" 제한시간 만료" + " [ userID : " + userId + ", battleId : " + battle.getId() + " ]");
                player.setUserStatus(UserStatus.Gaming);
                SseUtil.sendToClient(player.getEmitter(), "timeOut", "제한시간이 만료되었습니다.");

                if (battle.getType() == BattleType.ONEQUESTION)
                    player.getEmitter().complete();

            }
        }, 1000 * 20);             //문제 제한시간

        Response body = Response.builder()
                .status(StatusEnum.OK)
                .message("Get Question Success")
                .data(responseDto)
                .build();
        return new ResponseEntity<>(body, Response.getDefaultHeader(), HttpStatus.OK);
    }

    @PostMapping("/battle/answer")
    public ResponseEntity<Response> answer(
            @RequestBody AnswerDto answer) {
        Response body;
        UserDto player = SseService.allPlayers.get(answer.getUserId());

        Battle battle = player.getBattle();

        System.out.println("=== answer submitted");
        System.out.println("=== battle id : " + battle.getId() + ", user : " + answer.getUserId() + ", answer : " + answer.getAnswer());

        if (player.getAnswerCount() == Battle.MAX_ANSWER_COUNT) {
            player.setUserStatus(UserStatus.ExceedAnswerCount);
        }

        UserStatus status = player.getUserStatus();
        switch (status) {
            case AbleAnswer -> {
                player.increaseAnswerCount();

                int questionIdx = battle.getQuestionIdxByUserId(answer.getUserId()) + 1;
                Boolean isCorrect = questionService.checkAnswer(answer, battle);

                if(isCorrect) {
                    player.getAnswerTimer().cancel();
                    player.getOpponent().getAnswerTimer().cancel();
                    if(battle.getType() == BattleType.ONEQUESTION){
                        player.getOpponent().setUserStatus(UserStatus.Gaming);
                        player.setUserStatus(UserStatus.Gaming);

                        for (String key : battle.getPlayers().keySet()) {                           // 해당 배틀에 참여중인 상대방 & 자신에게 정답 여부 전달 (sse)
                            SseEmitter emitter = battle.getPlayers().get(key).getEmitter();
                            SseUtil.sendToClient(emitter, "answer-result",
                                    AnswerResultDto.builder()
                                            .userId(answer.getUserId())
                                            .questionIdx(Integer.toString(questionIdx))
                                            .isCorrect(true)
                                            .build());
                            emitter.complete();
                        }

                    }
                }else{
                    SseUtil.sendToClient(player.getEmitter(), "answer-result",
                            AnswerResultDto.builder()
                                    .userId(answer.getUserId())
                                    .questionIdx(Integer.toString(questionIdx))
                                    .isCorrect(false)
                                    .build());
                }


                body = Response.builder()
                        .status(StatusEnum.OK)
                        .data("answer submit success")
                        .message("answer submit success")
                        .build();
            }
            case ExceedAnswerCount -> {
                body = Response.builder()
                        .status(StatusEnum.BAD_REQUEST)
                        .message("exceed max answer count")
                        .build();
            }
            default -> {
                body = Response.builder()
                        .status(StatusEnum.BAD_REQUEST)
                        .message("timeout")
                        .build();
            }
        }

        return new ResponseEntity<>(body, Response.getDefaultHeader(), HttpStatus.OK);
    }
}
