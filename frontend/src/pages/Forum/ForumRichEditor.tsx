import { CKEditor } from '@ckeditor/ckeditor5-react';
import {
  BlockQuote,
  Bold,
  ClassicEditor,
  Code,
  CodeBlock,
  Essentials,
  Heading,
  Image,
  ImageCaption,
  ImageResize,
  ImageStyle,
  ImageTextAlternative,
  ImageToolbar,
  ImageUpload,
  Italic,
  Link,
  List,
  Paragraph,
  Underline,
  FileLoader,
  FileRepository,
  type Editor,
  type UploadAdapter,
} from 'ckeditor5';
import 'ckeditor5/ckeditor5.css';
import { uploadForumAttachment } from '../../services';
import type { ForumAttachment } from '../../services';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

function validateImage(file: File) {
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) return 'Chỉ hỗ trợ JPG/PNG/WebP/GIF trong editor';
  if (file.size > MAX_IMAGE_BYTES) return 'Ảnh tối đa 5MB';
  return '';
}

class ForumUploadAdapter implements UploadAdapter {
  private loader: FileLoader;
  private onUploaded: (attachment: ForumAttachment) => void;

  constructor(loader: FileLoader, onUploaded: (attachment: ForumAttachment) => void) {
    this.loader = loader;
    this.onUploaded = onUploaded;
  }

  async upload() {
    const file = await this.loader.file;
    if (!file) throw new Error('No file');
    const validationError = validateImage(file);
    if (validationError) throw new Error(validationError);

    const uploaded = await uploadForumAttachment(file);
    this.onUploaded(uploaded);
    return { default: uploaded.url };
  }

  abort() {}
}

function ForumUploadAdapterPlugin(onUploaded: (attachment: ForumAttachment) => void) {
  return function uploadAdapterPlugin(editor: Editor) {
    const fileRepository = editor.plugins.get('FileRepository') as FileRepository;
    fileRepository.createUploadAdapter = (loader) => new ForumUploadAdapter(loader, onUploaded);
  };
}

interface ForumRichEditorProps {
  value?: string;
  onChange: (value: string) => void;
  onUploadedAttachment?: (attachment: ForumAttachment) => void;
  placeholder?: string;
  disabled?: boolean;
}

export default function ForumRichEditor({
  value,
  onChange,
  onUploadedAttachment,
  placeholder,
  disabled = false,
}: ForumRichEditorProps) {
  return (
    <CKEditor
      editor={ClassicEditor}
      disabled={disabled}
      data={value || ''}
      config={{
        licenseKey: 'GPL',
        placeholder,
        extraPlugins: onUploadedAttachment ? [ForumUploadAdapterPlugin(onUploadedAttachment)] : undefined,
        plugins: [
          Essentials,
          Paragraph,
          Heading,
          Bold,
          Italic,
          Underline,
          Link,
          List,
          BlockQuote,
          Code,
          CodeBlock,
          Image,
          ImageUpload,
          ImageToolbar,
          ImageStyle,
          ImageResize,
          ImageCaption,
          ImageTextAlternative,
        ],
        toolbar: [
          'heading',
          '|',
          'bold',
          'italic',
          'underline',
          'link',
          '|',
          'bulletedList',
          'numberedList',
          'blockQuote',
          '|',
          'code',
          'codeBlock',
          '|',
          'imageUpload',
          '|',
          'undo',
          'redo',
        ],
        image: {
          toolbar: ['imageTextAlternative', '|', 'imageStyle:inline', 'imageStyle:block', 'imageStyle:side', '|', 'resizeImage'],
        },
      }}
      onChange={(_, editor) => onChange(editor.getData())}
    />
  );
}
