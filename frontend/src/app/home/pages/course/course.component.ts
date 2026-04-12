import { Component, OnInit } from '@angular/core';
import { SubjectService } from './services/subject.service';
import { SubjectResponse } from './models/subject.model';

@Component({
  selector: 'app-course',
  templateUrl: './course.component.html',
  styleUrls: ['./course.component.scss']
})
export class CourseComponent implements OnInit {
  allSubjects: SubjectResponse[] = []; // Сюда упадут данные из базы

  constructor(private subjectService: SubjectService) {}

  ngOnInit(): void {
    this.loadSubjects();
  }

  loadSubjects(): void {
    // Вызываем метод сервиса, который стучится в subject-service/api/subjects
    this.subjectService.getAllSubjects().subscribe({
      next: (data: SubjectResponse[]) => {
        this.allSubjects = data;
        console.log('Предметы загружены:', data);
      },
      error: (err: any) => console.error('Ошибка загрузки предметов:', err)
    });
  }
}