package com.battle.csbattle.service;

import com.battle.csbattle.battle.Battle;
import com.battle.csbattle.battle.BattleStatus;
import com.battle.csbattle.dto.AnswerDto;
import com.battle.csbattle.dto.QuestionDto;
import com.battle.csbattle.dto.UserDto;
import com.battle.csbattle.entity.Question;
import com.battle.csbattle.entity.QuestionType;
import com.battle.csbattle.repository.QuestionRepository;
import com.battle.csbattle.util.SseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionService {

    private final QuestionRepository questionRepository;
    // TODO: 함수 작성
    public QuestionDto getOneQuestionByQuestionType(QuestionType questionType) { // TODO : 코드 추가하기
        QuestionDto questionDto = new QuestionDto();

        return questionDto;
    }

    public String oneQuestionAndResponse(Battle battle,String userId){
        battle.setBattleStatus(BattleStatus.AbleAnswer);
        QuestionDto questionDto=QuestionDto.from(questionRepository.findRandomOne());
//        for(String key:battle.getPlayers().keySet()){
//            SseUtil.sendToClient(battle.getPlayers().get(key).getEmitter(),"Question",questionDto);
//        }
        SseUtil.sendToClient(battle.getPlayers().get(userId).getEmitter(),"Question",questionDto);
        try {
            Thread.sleep(1000*10);
        }catch (InterruptedException e){
            System.out.println(e.getMessage());
        }
        battle.setBattleStatus(BattleStatus.Gaming);
        return "제한시간이 만료되었습니다.";
    }
    public QuestionDto getOneQuestion(Battle battle) {              // 한 문제 불러오기 (해당 battle 에 대해 출제해줄 문제 선정)
        List<QuestionDto> battleQuestion = battle.getQuestions();
        List<Question> questions = questionRepository.findAll();

        Random random = new Random();
        int index = random.nextInt(questions.size());

        QuestionDto returnQuestion;
        if(battleQuestion.size() == 0) {
            returnQuestion = QuestionDto.from(questions.get(index));
            battle.addQuestion(returnQuestion);
        }
        else {
            returnQuestion = battleQuestion.get(0);
        }

        return returnQuestion;
    }

    public void addQuestionsToBattle(Battle battle, int count){
        List<QuestionDto> questions = battle.getQuestions();
        List<Question> questionList = questionRepository.findAll();
        count += questions.size();

        while(questions.size() < count){
            Random random = new Random();
            int index = random.nextInt(questionList.size());

            QuestionDto question = QuestionDto.from(questionList.get(index));

            if(!questions.contains(question)){
                questions.add(question);
                questionList.remove(index);
            }
        }

        battle.setQuestions(questions);
    }
    public Boolean checkAnswer(Long questionId, AnswerDto answer) {
        Question question = questionRepository.findById(questionId).get();

        Boolean isCorrect = false;
        if(question.getAnswer().equals(answer.getAnswer())){ isCorrect = true; }

        return isCorrect;
    }
}