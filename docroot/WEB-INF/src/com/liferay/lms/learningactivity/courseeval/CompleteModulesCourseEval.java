package com.liferay.lms.learningactivity.courseeval;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import com.liferay.lms.model.Course;
import com.liferay.lms.model.CourseResult;
import com.liferay.lms.model.LearningActivity;
import com.liferay.lms.model.LearningActivityResult;
import com.liferay.lms.model.Module;
import com.liferay.lms.model.ModuleResult;
import com.liferay.lms.service.CourseResultLocalServiceUtil;
import com.liferay.lms.service.LearningActivityLocalServiceUtil;
import com.liferay.lms.service.LearningActivityResultLocalServiceUtil;
import com.liferay.lms.service.LearningActivityTryLocalServiceUtil;
import com.liferay.lms.service.ModuleLocalServiceUtil;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.model.User;
import com.liferay.portal.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.service.UserLocalServiceUtil;

public class CompleteModulesCourseEval extends BaseCourseEval {

	@Override
	public void updateCourse(Course course, ModuleResult mresult) throws SystemException 
	{
		
		updateCourse(course, mresult.getUserId());	

	}
	
	@Override
	public boolean updateCourse(Course course, long userId) throws SystemException {

		// Se obtiene el courseresult del usuario en dicho course.
		long courseId = course.getCourseId();
		long groupCreatedId = course.getGroupCreatedId();
		CourseResult courseResult = CourseResultLocalServiceUtil.getByUserAndCourse(courseId, userId);

		// Si el resultado del curso del usuario es null, es porque el usuario no ha iniciado aún el curso en cuestión.
		if(courseResult==null) {
			courseResult = CourseResultLocalServiceUtil.create(courseId, userId);
		}

		if(courseResult.getStartDate() == null){
			courseResult.setStartDate(new Date());
		}
		
		// Se obtienen todos los módulos del curso.
		List<Module> modules = ModuleLocalServiceUtil.findAllInGroup(groupCreatedId);
		long numModules = modules.size();

		boolean passed = true;
		long result = 0;
		// Se obtienen los módulos aprobados por el usuario.
		long modulesPassedByUser = ModuleLocalServiceUtil.modulesUserPassed(groupCreatedId, userId);
		// Se calcula el resultado del usuario.
		result = 100 * modulesPassedByUser / numModules;

		// Se obtienen las actividades que son obligatorias en el curso.
		List<LearningActivity> learningActivities = LearningActivityLocalServiceUtil.getMandatoryLearningActivitiesOfGroup(groupCreatedId);
		
		//Guardo los resultados de las actividades del usuario en el curso en un hashmap para no tener que acceder a bbdd por cada uno de ellos
		List<LearningActivityResult> lresult = LearningActivityResultLocalServiceUtil.getMandatoryByGroupIdUserId(course.getGroupCreatedId(), userId);
		HashMap<Long, LearningActivityResult> results = new HashMap<Long, LearningActivityResult>();
		for(LearningActivityResult ar:lresult){
			results.put(ar.getActId(), ar);
		}
		
		boolean isFailed = false;
		long currentActId = 0, numTriesDone = 0, numTriesCurrentAct;
		LearningActivityResult lar = null;
		// Se iteran por las actividades obligatorias para comprobar si se tienen resultados de las mismas y se tienen aprobadas.
		for(LearningActivity activity:learningActivities) {   
			currentActId = activity.getActId();
			
			if(results.containsKey(currentActId)){
				lar = results.get(currentActId);
			}else{
				lar = null;
			}
			
			// Si el usuario no tiene resultado en la actividad.
			if(lar != null) {
				if (!lar.isPassed() && Validator.isNotNull(lar.getEndDate())) {
					numTriesCurrentAct = activity.getTries();
					// Si la actividad no tiene un número ilimitado de intentos (numTriesCurrentAct = 0) y el usuario ya ha hecho todos los intentos disponibles se marca el curso como "Suspenso" (isFailed). 
					if(numTriesCurrentAct != 0) {						
						numTriesDone = LearningActivityTryLocalServiceUtil.getLearningActivityTryByActUserCount(currentActId, userId);					
						
						if (numTriesCurrentAct <= numTriesDone) {
							isFailed = true;
						}
					}
					passed = false;
				}
				else{
					passed = false;
				}
			}else {
				passed = false;
			}
		}
		
		// Si el usuario se ha marcado como isFailed es porque lo tiene suspenso. Se le asigna un passed a false y se marca la fecha de finalización del curso (passedDate).
		courseResult.setPassed(passed && !isFailed);
		// Se almacena el result del resultado del usuario en el curso.
		courseResult.setResult(result);
		if((passed || isFailed) && courseResult.getPassedDate() == null) {
			courseResult.setPassedDate(new Date());
		}
		CourseResultLocalServiceUtil.update(courseResult);
		return true;

	}

	
	@Override
	public boolean updateCourse(Course course) throws SystemException {
		
		List<Module> modules=ModuleLocalServiceUtil.findAllInGroup(course.getGroupCreatedId());
		try {
		
			for(User userOfCourse:UserLocalServiceUtil.getGroupUsers(course.getGroupCreatedId())){
				if(!PermissionCheckerFactoryUtil.create(userOfCourse).hasPermission(course.getGroupCreatedId(), "com.liferay.lms.model",course.getGroupCreatedId(), "VIEW_RESULTS")){
			
				CourseResult courseResult=CourseResultLocalServiceUtil.getByUserAndCourse(course.getCourseId(), userOfCourse.getUserId());
				if(courseResult==null)
				{
					courseResult=CourseResultLocalServiceUtil.create(course.getCourseId(), userOfCourse.getUserId());
					courseResult.setStartDate(new Date());
				}
				
	
				
				boolean passed=true;
				long cuantospasados=0;
				for(Module thmodule:modules)
				{
					if(!ModuleLocalServiceUtil.isUserPassed(thmodule.getModuleId(), userOfCourse.getUserId()))
					{
						passed=false;
						
					}
					else
					{
						cuantospasados++;
					}
				}
				long result=0;
				if(modules.size()>0)
				{
					result=100*cuantospasados/modules.size();
				}
			
				courseResult.setResult(result);

				if(courseResult.getPassed()!=passed) {
					courseResult.setPassedDate(new Date());
				}
				courseResult.setPassed(passed);

				CourseResultLocalServiceUtil.update(courseResult);
				
				}				
			}
		} catch (Exception e) {
			throw new SystemException(e);
		}
		return true;	
	}
	
	@Override
	public String getName() {
		return "courseeval."+getTypeId()+".name";
	}
	
	@Override
	public String getName(Locale locale) {
		return LanguageUtil.get(locale, "courseeval."+getTypeId()+".name");
	}

	@Override
	public long getTypeId() {
		return 0;
	}

	@Override
	public boolean getNeedPassAllModules() {
		return true;
	}

	@Override
	public boolean getFailOnCourseCloseAndNotQualificated() {
		return true;
	}

	@Override
	public boolean getNeedPassPuntuation() {
		return false;
	}

	@Override
	public long getPassPuntuation(Course course) throws DocumentException {
		throw new RuntimeException();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onOpenCourse(Course course) throws SystemException {
		for(CourseResult courseResult:
			(List<CourseResult>)CourseResultLocalServiceUtil.dynamicQuery(
				CourseResultLocalServiceUtil.dynamicQuery().
					add(PropertyFactoryUtil.forName("courseId").eq(course.getCourseId())).
					add(PropertyFactoryUtil.forName("passed").eq(false)))){
			courseResult.setPassedDate(null);
			CourseResultLocalServiceUtil.update(courseResult);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onCloseCourse(Course course) throws SystemException {
		for(CourseResult courseResult:
			(List<CourseResult>)CourseResultLocalServiceUtil.dynamicQuery(
				CourseResultLocalServiceUtil.dynamicQuery().
					add(PropertyFactoryUtil.forName("courseId").eq(course.getCourseId())).
					add(PropertyFactoryUtil.forName("passedDate").isNull()))){
			courseResult.setPassedDate(course.getModifiedDate());
			CourseResultLocalServiceUtil.update(courseResult);
		}
	}

	@Override
	public JSONObject getEvaluationModel(Course course) throws PortalException,
			SystemException, DocumentException, IOException {
		return JSONFactoryUtil.createJSONObject();
	}

	@Override
	public void setEvaluationModel(Course course, JSONObject model)
			throws PortalException, SystemException, DocumentException,
			IOException {	
		Document document = SAXReaderUtil.createDocument();
		Element rootElement = document.addElement("eval");
		rootElement.addElement("courseEval").setText(CompleteModulesCourseEval.class.getName());		
		course.setCourseExtraData(document.formattedString());
	}

}
