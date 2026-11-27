// Explicit, experimental assignments. No domain-wide fallback.
const groups = [
 ['emotion',10,'최근 스트레스·노출 환경에 따른 변화를 비교적 빠르게 수용','anticipation emotional_contagion mood_amplitude recovery_speed monotony_boredom crowding_overload'],
 ['emotion',20,'서로 다른 상황의 반복 근거를 확인하며 반영','uncertainty_anxiety blocked_frustration injustice_anger loss_sadness positive_joy public_embarrassment emotion_duration'],
 ['emotion',40,'기본 반응 경향을 일시적인 경험으로 크게 바꾸지 않도록 보수적으로 반영','threat_fear evaluation_sensitivity novelty_stimulation noise_sensitivity visual_sensitivity smell_sensitivity touch_sensitivity'],
 ['values',20,'최근 생활 조건·목표에 따른 중요도 변화를 수용','financial_comfort health pleasure recognition'],
 ['values',40,'지속적인 가치 판단을 반복 근거로 갱신','autonomy security achievement fairness care family growth tradition contribution honesty loyalty privacy creativity expertise environment spirituality'],
 ['interests',10,'최근 접하는 대상과 관심 변화에 빠르게 반응하도록 실험','technology society finance film_performance gaming fashion'],
 ['interests',20,'환경과 경험의 영향 및 지속적인 선호를 함께 반영','science sports travel outdoors cooking_food social_gatherings'],
 ['interests',40,'장기간 유지되는 취향이라는 가설로 보수적으로 시작','history philosophy literature visual_art music craft animals collecting'],
 ['decision',10,'최근 선택 환경과 활용 가능한 자원에 따른 변화를 수용','consultation deliberation_time small_experiments context_adaptation'],
 ['decision',20,'반복적인 선택 사례를 통해 조정','research_depth alternative_breadth risk_taking explicit_criteria belief_revision satisficing default_retention tradeoffs'],
 ['decision',40,'기본 판단 경향을 보수적으로 유지','source_verification quantitative_reasoning intuition loss_aversion long_term reversibility opportunity_cost contingency'],
 ['habits',10,'최근의 실제 생활 변화가 점수에 드러나도록 반영','wake_regularity sleep_regularity meal_regularity exercise health_checks reading multitasking rest'],
 ['habits',20,'반복 실행을 확인하며 중간 속도로 반영','plan_recording plan_execution physical_order digital_order expense_tracking budget_adherence saving learning procrastination review'],
 ['habits',40,'단발성 지연이나 성공으로 신뢰 관련 행동 경향을 크게 바꾸지 않도록 보수적으로 반영','deadline punctuality'],
 ['relations',10,'최근 관계·역할 변화에 반응','contact_initiation self_disclosure ask_help role_coordination leadership repair'],
 ['relations',20,'여러 관계에서 나타난 행동을 함께 반영','stranger_approach intimacy_speed assert_boundaries give_help conflict_facing compromise assert_position apology perspective_check'],
 ['relations',40,'지속적인 관계 경향을 우선 보존','social_orientation personal_space respect_boundaries trust_speed reciprocity'],
 ['expression',10,'최근 대화 환경·주제에 따른 표현 변화를 수용','formality certainty emotion_words emphasis questions terminology slang emoji'],
 ['expression',20,'여러 발언에서 나타나는 형식적 패턴을 반영','sentence_length detail clarification structure'],
 ['expression',40,'기본적인 표현 습관을 보수적으로 유지','directness point_first examples metaphors humor irony hedging personal_examples']
];
const policies = new Map();
for (const [domain,maxWeight,rationale,keys] of groups) for (const key of keys.split(' ')) {
 const code=domain+'.'+key;
 if(policies.has(code))throw Error('Duplicate update policy: '+code);
 policies.set(code,{maxWeight,rationale});
}
if(policies.size!==140)throw Error('140 explicit update policies required');
module.exports={version:'element-cap-v1',policies};
