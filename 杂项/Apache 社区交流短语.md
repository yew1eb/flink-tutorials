## 缩写：
ML —— mail list 
AFAIK —— as far as I know
JAM —— Just a moment
OIC —— Oh,I see
IDK —— I don't know
IMO —— in my opinion
IIRC —— if I remember correctly
TBD —— to be determined
TBC —— to be continued
BTW —— by the way
Make sense
Out of curiosity

## Review别人代码的时候：

LGTM, once Travis is green.
LGTM, Changes look good to me.
Perfect, this LGTM now!       
            

## 回复别人的Review建议：      
Thank you for your respenses.
Thanks for the review @StephanEwen. I will address your comments and then merge this PR

 Hi, @fhueske Thanks for your attention to the PR. Today I'll proposal the design doc and link here.

HI, @fhueske Thanks a lot for your review. I had updated the PR according to your comments.

@StephanEwen I had update the PR. I appreciated if you can review this PR.

thanks for your review. I have updated the PR.

Thanks for letting me know. I'll close this PR

@zentol Thanks a bunch for your review. I have updated the code.


 @tzulitai Let me know what you think about this PR now :)


@zentol Please let me know if this is good :)

@tzulitai Hi Gordon, can you please take a look at this PR?

## Issue 任务领取

I would like to work on this issue.

What do you think about this ticket? 

HI, @aljoscha Thank you for your attention to this JIRA.


## 讨论：
IMO. The main purpose of doing this PR is to enhance the function of Flat/MapRunner. In addition, my next plan is:
Use CODE-GEN to generate the class which below the org.apache.flink.table.runtime.aggregatepackage, perhaps this PR will help me in the next work. What do you think? 
HI, @KurtYoung Thanks for your attention to this PR. Good question, Here I glad share why I notice this method： 
Hi @fhueske thanks a lot for your review. I have updated the PR according to your comments.
Hi @fhueske , yes, Calcite forces an Calc after each aggregate that only renames fields, because we rename every aggregates in Table API which is not necessary. I changed the logic of getting projections on aggregates to only rename the duplicate aggregates. And that works good, no more Calc appended. 
Hi @haohui , the ArrayRelDataType is still NOT NULL. I reverted that line which is not need to be changed in this PR.
Hi @twalthr , I addressed the comments. Thank you for your suggestion. I add some tests that getting schema from tables in existing ITCases.
@zentol I have created this PR to fix https://issues.apache.org/jira/browse/FLINK-6787, could you please have a look when you're free, thanks :)
Thanks @dawidwys for the suggestion. I will update the PR accordingly.
 Hi @aljoscha @tillrohrmann , what do you think about this?
Hi @wuchong, thanks for the update. I only have a few minor comments.

One thing that might be worth to check if whether the generic based extraction is more likely to fail than the previous approach of analyzing an instance. @twalthr knows the TypeExtractor very well and might be able to answer that question.

Hi @StefanRRichter , do you have more feedbacks?

Kostas Kloudas Thanks for pointing out this! After reading your comments, I agree with you that the issue is in applyToAllKeys(), and I also like the solution you suggested very much. I will change the code to meet your suggestion.

Hi @kl0u I changed a bit of the implementation of JIRA, instead of implement multi wrapper classes for different `State`, I introduce a `StateInvocationHandler` which implemented `InvocationHandler` to delegate the `clear()` method of `State` , could you please have a look at this? and please let me know if you have any advice.
Do you have any suggestion on how to proceed?
how about your opinion about this issue?
do you still feel it's necessary to .
i' will run some tests with this patch tonight.
i've observed the xxx failing on travis.

## 表明你的意思
anything wrong please correct me.
I agree with xxx that
Personally, I would favour option No.3


## 邮件提案

dev@flink.apache.org

Hello Flink devs,

I'd like to propose removinh of flink-contrib/flink-streaming-contrib and
migrate its classes to flink-streaming-java/scala, for the following
reasons:

1. flink-streaming-contrib is so small that has only 4 classes (3 java and
1 scala), which isn't worth a dedicated jar for Flink to distribute and
maintain it, and for users to deal with confusion and overhead of
dependency management
2. the 4 classes are logically more tied to flink-streaming-java/scala, and
thus can be easily migrated
3. flink-contrib is already crowded and noisy. It lacks a proper project
hierarchy, confusing both devs and users

More discussion details in FLINK-8167
<https://issues.apache.org/jira/browse/FLINK-8167> and FLINK-8175
<https://issues.apache.org/jira/browse/FLINK-8175>

Are there any objections to this?

* This can be the 1st step of cleaning up flink-contrib. Stay tuned

Thanks,
Bowen
Thank you Shuyi, I will investigate these issues.

[Bowen Li](https://issues.apache.org/jira/secure/ViewProfile.jspa?name=phoenixjiangnan) Sorry for I did not reply for so long, because I have been busy with other work lately.



Sorry for slow late reply here.

