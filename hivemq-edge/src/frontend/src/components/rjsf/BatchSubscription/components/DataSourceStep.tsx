import { FC, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useDropzone } from 'react-dropzone'
import * as XLSX from 'xlsx'
import { AlertStatus, Button, Text, useToast, VStack } from '@chakra-ui/react'

import { acceptMimeTypes } from '@/components/rjsf/BatchSubscription/utils/config.utils.ts'
import { readFileAsync } from '@/components/rjsf/BatchSubscription/utils/dropzone.utils.ts'
import { StepRendererProps, WorksheetData } from '@/components/rjsf/BatchSubscription/types.ts'
import { DEFAULT_TOAST_OPTION } from '@/hooks/useEdgeToast/toast-utils.ts'

const getDropZoneBorder = (color: string) => {
  return {
    bgGradient: `repeating-linear(0deg, ${color}, ${color} 10px, transparent 10px, transparent 20px, ${color} 20px), repeating-linear-gradient(90deg, ${color}, ${color} 10px, transparent 10px, transparent 20px, ${color} 20px), repeating-linear-gradient(180deg, ${color}, ${color} 10px, transparent 10px, transparent 20px, ${color} 20px), repeating-linear-gradient(270deg, ${color}, ${color} 10px, transparent 10px, transparent 20px, ${color} 20px)`,
    backgroundSize: '2px 100%, 100% 2px, 2px 100% , 100% 2px',
    backgroundPosition: '0 0, 0 0, 100% 0, 0 100%',
    backgroundRepeat: 'no-repeat',
    borderRadius: '4px',
  }
}

const DataSourceStep: FC<StepRendererProps> = ({ onContinue, store }) => {
  const { t } = useTranslation('components')
  const toast = useToast()
  const [loading, setLoading] = useState(false)
  const { fileName } = store
  const { getRootProps, getInputProps, isDragActive, open } = useDropzone({
    noClick: true,
    noKeyboard: true,
    maxFiles: 1,
    accept: acceptMimeTypes,
    onDropRejected: (fileRejections) => {
      const status: AlertStatus = 'error'
      setLoading(false)
      fileRejections.forEach((fileRejection) => {
        toast({
          ...DEFAULT_TOAST_OPTION,
          status,
          title: t('rjsf.batchUpload.dropZone.status', { context: status, fileName: fileRejection.file.name }),
          description: fileRejection.errors[0].message,
        })
      })
    },
    onDropAccepted: async (files) => {
      const [file] = files
      setLoading(true)
      try {
        const workbook = XLSX.read(await readFileAsync(file), {
          dense: true,
        })
        const firstWorksheetAsRawData = workbook.Sheets[workbook.SheetNames[0]]
        const firstWorksheetData = XLSX.utils.sheet_to_json<WorksheetData>(firstWorksheetAsRawData)

        const status: AlertStatus = 'success'
        toast({
          ...DEFAULT_TOAST_OPTION,
          status,
          title: t('rjsf.batchUpload.dropZone.status', { context: status, fileName: file.name }),
        })
        onContinue({ worksheet: firstWorksheetData, fileName: file.name })
      } catch (error) {
        let message
        if (error instanceof Error) message = error.message
        else message = String(error)
        const status: AlertStatus = 'error'
        toast({
          ...DEFAULT_TOAST_OPTION,
          status,
          title: t('rjsf.batchUpload.dropZone.status', { context: status, fileName: file.name }),
          description: message,
        })
      }

      setLoading(false)
    },
  })

  return (
    <VStack
      {...getRootProps()}
      {...getDropZoneBorder('blue.500')}
      minHeight="calc(450px - 2rem)"
      display="flex"
      justifyContent="center"
      alignItems="center"
      id="dropzone"
    >
      <input {...getInputProps()} data-testid="batch-load-dropzone" />
      {isDragActive && <Text>{t('rjsf.batchUpload.dropZone.dropping')}</Text>}
      {loading && <Text>{t('rjsf.batchUpload.dropZone.loading')}</Text>}
      {!isDragActive && !loading && (
        <>
          {fileName && <Text mb={4}>{t('rjsf.batchUpload.dropZone.currentlyLoaded', { fileName: fileName })}</Text>}
          <Text>{t('rjsf.batchUpload.dropZone.placeholder')}</Text>
          <Button onClick={open}>{t('rjsf.batchUpload.dropZone.selectFile')}</Button>
        </>
      )}
    </VStack>
  )
}

export default DataSourceStep
